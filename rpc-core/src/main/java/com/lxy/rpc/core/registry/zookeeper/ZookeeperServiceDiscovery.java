package com.lxy.rpc.core.registry.zookeeper;

import com.lxy.rpc.core.common.exception.RegistryException;
import com.lxy.rpc.core.registry.ServiceDiscovery;
import com.lxy.rpc.core.registry.ServiceInstancesChangeListener;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于zookeeper实现的服务发现
 */
public class ZookeeperServiceDiscovery implements ServiceDiscovery {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperServiceDiscovery.class);
    // zookeeper客户端
    private final CuratorFramework zkClient;
    // 本地服务：服务名 -> 服务实例地址列表
    private final Map<String, List<InetSocketAddress>> serviceAddressCache = new ConcurrentHashMap();
    // 缓存CuratorCache列表，用于后续订阅和取消订阅：服务名 -> CuratorCache
    private final Map<String, CuratorCache> serviceWatcherCaches = new ConcurrentHashMap();
    // 缓存监听器：服务名 -> 监听器列表 (因为一个服务可能被多个地方订阅)
    private final Map<String, List<ServiceInstancesChangeListener>> serviceChangeListeners = new ConcurrentHashMap();

    public ZookeeperServiceDiscovery(String zkAddress) {
        try {
            this.zkClient = CuratorFrameworkFactory.builder()
                    .connectString(zkAddress)
                    .sessionTimeoutMs(ZookeeperConstant.ZK_SESSION_TIMEOUT)
                    .connectionTimeoutMs(ZookeeperConstant.ZK_CONNECTION_TIMEOUT)
                    .retryPolicy(new ExponentialBackoffRetry(
                            ZookeeperConstant.CURATOR_RETRY_BASE_SLEEP_MS,
                            ZookeeperConstant.CURATOR_RETRY_MAX_RETRIES
                    ))
                    .build();
            this.zkClient.start();
            logger.info("ZookeeperServiceDiscovery: 创建Zookeeper服务发现成功，地址为 {}", zkAddress);
        } catch (Exception e) {
            logger.error("ZookeeperServiceDiscovery: 创建Zookeeper服务发现失败，地址为 {}", zkAddress, e);
            throw new RegistryException("ZookeeperServiceDiscovery: 创建Zookeeper服务发现失败，地址为 " + zkAddress, e);
        }
    }

    @Override
    public List<InetSocketAddress> discoverService(String serviceName) {
        // 1. 优先查询本地换缓存
        List<InetSocketAddress> cachedAddresses = serviceAddressCache.get(serviceName);
        if (cachedAddresses != null && !cachedAddresses.isEmpty()) {
            logger.debug("ZookeeperServiceDiscovery: 从本地缓存中获取服务 {} 的实例列表成功", serviceName);
            return cachedAddresses;
        }

        // 2. 缓存中没有，则从zookeeper中获取
        String servicePath = ZookeeperConstant.ZK_RPC_ROOT_PATH + "/" + serviceName;
        try {
            // 判断服务路径是否存在
            if (zkClient.checkExists().forPath(servicePath) == null) {
                logger.warn("ZookeeperServiceDiscovery: 路径 {} 下的服务 {} 不存在", servicePath, serviceName);
                serviceAddressCache.put(serviceName, new ArrayList<>()); // 创建一个空的实例列表，避免频繁查询
                return new ArrayList<>();
            }

            // 判断服务实例列表是否存在
            List<String> childrenNodes = zkClient.getChildren().forPath(servicePath);
            if (childrenNodes == null || childrenNodes.isEmpty()) {
                logger.warn("ZookeeperServiceDiscovery: 路径 {} 下的服务 {} 的实例列表为空", servicePath, serviceName);
                serviceAddressCache.put(serviceName, new ArrayList<>()); // 创建一个空的实例列表，避免频繁查询
                return new ArrayList<>();
            }

            List<InetSocketAddress> discoveredAddresses = childrenNodes.stream()
                    .map(nodeName -> {
                        // 节点名可能是 "ip:port-xxxxxxxxxx" (有序节点) 或 "ip:port"
                        // 我们需要从节点名中解析出 "ip:port"
                        // 这里简化处理，假设节点名直接就是 "ip:port" 或者可以从中提取
                        // 如果节点存储了数据，则从数据中获取地址
                        // byte[] data = zkClient.getData().forPath(servicePath + "/" + nodeName);
                        // String addressStr = new String(data, StandardCharsets.UTF_8);
                        String addressStr = parseAddressFromNodeName(nodeName);
                        return RpcFrameworkUtils.parseAddress(addressStr);
                    })
                    .filter(address -> address != null)
                    .collect(Collectors.toList());
            logger.info("ZookeeperServiceRegistry: 获取服务 {} 的服务实例成功, 服务实例列表为 {}",
                    serviceName, discoveredAddresses);
            // 更新本地缓存
            serviceAddressCache.put(serviceName, discoveredAddresses);
            return discoveredAddresses;
        } catch (Exception e) {
            logger.error("ZookeeperServiceRegistry: 获取服务 {} 的服务实例失败", serviceName, e);
            // 出错时返回缓存（可能是旧的但可用），或者空列表
            return serviceAddressCache.getOrDefault(serviceName, new ArrayList<>());
        }
    }

    @Override
    public void subscribe(String serviceName, ServiceInstancesChangeListener listener) {
        if (listener == null) return;

        String servicePath = ZookeeperConstant.ZK_RPC_ROOT_PATH + "/" + serviceName;
        // 为该服务添加一个CuratorCache来监听服务节点的变化。
        CuratorCache cache = serviceWatcherCaches.computeIfAbsent(serviceName, k -> {
            CuratorCache newCache = CuratorCache.build(zkClient, servicePath);
            // 为该CuratorCache添加监听器
            newCache.listenable().addListener(CuratorCacheListener.builder()
                    .forPathChildrenCache(servicePath, zkClient, (client, event) -> {
                        // 暂时设置只要子节点有变化，就拉取服务节点列表，并更新到缓存中。
                        logger.info("ZookeeperServiceRegistry: 服务节点 {} 发生变化，重新拉取服务节点列表, event: {}, path: {}",
                                serviceName, event.getType(), event.getData().getPath());
                        // 重新拉取服务节点列表，并更新缓存
                        List<InetSocketAddress> latestAddresses = discoverServiceAndRefreshCache(serviceName);
                        // 通知所有订阅者
                        notifyListeners(serviceName, latestAddresses);
                    })
                    .build());
            try {
                newCache.start();
                logger.info("ZookeeperServiceDiscovery: 开启CuratorCache监视路径, {}", servicePath);
            } catch (Exception e) {
                logger.error("ZookeeperServiceDiscovery: 开启CuratorCache监视路径失败, {}", servicePath, e);
                throw new RegistryException("ZookeeperServiceDiscovery: 开启CuratorCache监视路径失败, " + servicePath, e);
            }
            return newCache;
        });

        // 将监视器添加到监听列表中
        serviceChangeListeners.computeIfAbsent(serviceName, key -> new ArrayList<>()).add(listener);
        logger.info("ZookeeperServiceDiscovery: 添加服务变化监听器, {}", serviceName);

        // 首次订阅时，立即获取一次最新列表并通知(也就是说，在首次启动时，其实并没有变化，上述过程不会触发，但总需要手动订阅一下)
        List<InetSocketAddress> initialAddresses = discoverServiceAndRefreshCache(serviceName);
        notifyListeners(serviceName, initialAddresses);
    }

    /**
     * 当服务发生变化时，通知所有监听者
     * @param serviceName 服务名
     * @param latestAddresses 最新的服务节点列表
     */
    private void notifyListeners(String serviceName, List<InetSocketAddress> latestAddresses) {
        List<ServiceInstancesChangeListener> listeners = serviceChangeListeners.get(serviceName);
        if (listeners != null && !listeners.isEmpty()) {
            logger.debug("ZookeeperServiceRegistry: 服务 {} 发生变化，通知 {} 个监听者", serviceName, listeners.size());
            listeners.forEach(listener ->
                    listener.onInstancesChange(serviceName, latestAddresses));
        }
    }

    /**
     * 订阅服务节点列表变化
     * @param serviceName 服务名称
     */
    private List<InetSocketAddress> discoverServiceAndRefreshCache(String serviceName) {
        // 和discoverService类似，但是从zk中强制获取
        String servicePath = ZookeeperConstant.ZK_RPC_ROOT_PATH + "/" + serviceName;
        try {
            if (zkClient.checkExists().forPath(servicePath) == null) {
                List<InetSocketAddress> instances = new ArrayList<>();
                serviceAddressCache.put(serviceName, instances);
                return instances;
            }
            List<String> childNodes = zkClient.getChildren().forPath(servicePath);
            List<InetSocketAddress> addresses = childNodes.stream()
                    .map(this::parseAddressFromNodeName)
                    .map(RpcFrameworkUtils::parseAddress)
                    .filter(addr -> addr != null)
                    .collect(Collectors.toList());
            // 更新缓存
            serviceAddressCache.put(serviceName, addresses);
            logger.debug("ZookeeperServiceRegistry: 更新服务 {} 的实例列表成功, 实例列表为 {}", serviceName, addresses);
            return addresses;
        } catch (Exception e) {
            logger.error("ZookeeperServiceRegistry: 更新服务 {} 的实例列表失败", serviceName, e);
            return serviceAddressCache.getOrDefault(serviceName, new ArrayList<>());
        }
    }

    @Override
    public void unsubscribe(String serviceName, ServiceInstancesChangeListener listener) {
        List<ServiceInstancesChangeListener> listeners = serviceChangeListeners.get(serviceName);
        if (listeners != null) {
            listeners.remove(listener);
            logger.info("ZookeeperServiceRegistry: Listener {} 取消订阅服务 {} 的实例变化", listener.getClass().getSimpleName(), serviceName);
            if (listeners.isEmpty()) {
                // 如果没有监听器了，可以关闭并移除CuratorCache
                CuratorCache watcherCache = serviceWatcherCaches.remove(serviceName);
                if (watcherCache != null) {
                    watcherCache.close();
                    logger.info("ZookeeperServiceRegistry: 移除并关闭服务 {} 的 CuratorCache", serviceName);
                }
                serviceChangeListeners.remove(serviceName); // 也要移除列表本身
            }
        }
    }

    @Override
    public void close() {
        serviceWatcherCaches.values().forEach(CuratorCache::close);
        serviceWatcherCaches.clear();
        serviceChangeListeners.clear();
        if (zkClient != null) {
            zkClient.close();
            logger.info("ZookeeperServiceRegistry: 停止 Zookeeper 客户端");
        }
    }

    /**
     * 辅助方法，从子节点名字中获得服务实例地址ip:port
     * @param nodeName 子节点名字,如果是有序节点则为ip:port-xxxxxx，通常为ip:port
     * @return ip:port
     */
    private String parseAddressFromNodeName(String nodeName){
        if (nodeName == null) return null;
        int dashIndex = nodeName.lastIndexOf("-");
        if (dashIndex > 0 && dashIndex < nodeName.length() - 1) {
            // 检查"-"后面的是否都是数字 (简单的判断序列号)
            boolean lookLikeSequence = true;
            for (int i = dashIndex + 1; i < nodeName.length(); i++) {
                if(!Character.isDigit(nodeName.charAt(i))) {
                    lookLikeSequence = false;
                    break;
                }
            }
            if (lookLikeSequence) {
                nodeName = nodeName.substring(0, dashIndex);
            }
        }
        return nodeName;
    }
}
