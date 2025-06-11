package com.lxy.rpc.core.registry.zookeeper;

import com.lxy.rpc.core.common.constant.RpcErrorMessages;
import com.lxy.rpc.core.common.exception.RegistryException;
import com.lxy.rpc.core.common.exception.RpcException;
import com.lxy.rpc.core.common.exception.RpcRegistryException;
import com.lxy.rpc.core.registry.ServiceRegistry;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * 基于zookeeper的服务注册类
 */
public class ZookeeperServiceRegistry implements ServiceRegistry {
    // 日志信息
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperServiceRegistry.class);
    // zookeeper客户端
    private final CuratorFramework zkClient;

    /**
     * 构造函数
     * @param zkAddress ZooKeeper服务器地址，例如 "host1:port1,host2:port2"
     */
    public ZookeeperServiceRegistry(String zkAddress) {
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
            this.zkClient.start();  // 启动客户端
            logger.info("[ZookeeperServiceRegistry] 成功连接到 zookeeper 服务注册，地址为 {}", zkAddress);
        } catch (Exception e) {
            logger.error("[ZookeeperServiceRegistry] 连接 zookeeper 服务注册失败", e);
            throw new RpcRegistryException("[ZookeeperServiceRegistry] " +
                    RpcErrorMessages.format(RpcErrorMessages.ZK_SERVICE_REGISTRY_FAILED, zkAddress, e));
        }
    }

    @Override
    public void registerService(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            // 构建根路径
            String serviceRootPath = ZookeeperConstant.ZK_RPC_ROOT_PATH + "/" + serviceName;
            // 创建根路径
            if (zkClient.checkExists().forPath(serviceRootPath) == null) {
                zkClient.create()
                        .creatingParentsIfNeeded() // 创建父节点
                        .withMode(CreateMode.PERSISTENT) //父节点应为持久节点，确保服务名始终存在
                        .forPath(serviceRootPath);
                logger.info("[ZookeeperServiceRegistry] 成功创建服务根路径 {}", serviceRootPath);
            }

            // 构建服务实例路径
            String instanceServicePrefix = serviceRootPath + "/" + RpcFrameworkUtils.formatAddress(inetSocketAddress) + "-";

            // 创建服务实例路径
            if (zkClient.checkExists().forPath(instanceServicePrefix) == null) {
                String actualInstancePath = zkClient.create()
                        .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                        .forPath(instanceServicePrefix);
                logger.info("[ZookeeperServiceRegistry] 为服务 {}, 在 zookeeper中创建实例路径 {}", serviceName, actualInstancePath);
            }
        } catch (Exception e) {
            logger.error("[ZookeeperServiceRegistry] 为服务 {} 注册服务实例失败, 在zookeeper的地址 {}",
                    serviceName, RpcFrameworkUtils.formatAddress(inetSocketAddress), e);
            throw new RpcRegistryException("[ZookeeperServiceRegistry] " +
                    RpcErrorMessages.format(RpcErrorMessages.REGISTER_SERVICE_FAILED, serviceName, e));
        }

    }

    @Override
    public void unregisterService(String serviceName, InetSocketAddress inetSocketAddress) {
        // 对于临时节点，当客户端会话结束时，ZooKeeper会自动删除它们。
        // 但提供一个显式的注销方法可能在某些场景下有用，例如优雅停机时。
        // 要精确删除一个临时有序节点，需要知道它完整的路径（包含序列号）。
        // 如果我们没有保存完整的路径，这里可以尝试查找并删除，或者依赖会话结束。
        // 简单起见，我们目前依赖临时节点的自动删除特性。
        logger.info("[ZookeeperServiceRegistry] 自动删除服务 {} 的实例 {}",
                serviceName, RpcFrameworkUtils.formatAddress(inetSocketAddress));
    }

    @Override
    public void close() {
        if (zkClient != null) {
            zkClient.close();
            logger.info("[ZookeeperServiceRegistry] 关闭 Zookeeper 客户端");
        }
    }
}
