package com.lxy.rpc.core.client;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.common.constant.RpcErrorMessages;
import com.lxy.rpc.core.common.exception.*;
import com.lxy.rpc.core.loadbalance.LoadBalanceStrategy;
import com.lxy.rpc.core.loadbalance.RoundRobinLoadBalance;
import com.lxy.rpc.core.protocol.*;
import com.lxy.rpc.core.registry.zookeeper.RpcFrameworkUtils;
import com.lxy.rpc.core.registry.zookeeper.ZookeeperServiceDiscovery;
import com.lxy.rpc.core.serialization.SerializerFactory;
import com.lxy.rpc.core.registry.ServiceDiscovery;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用于代理创建客户端代理对象，使用JDK动态代理
 */
public class RpcClientProxy implements InvocationHandler {
    // 记录日志
    private static final Logger logger = LoggerFactory.getLogger(RpcClientProxy.class);

    // 默认使用默认序列化算法
    private final byte serializerAlgorithm;

    // 用于生成请求ID，确保在单个客户端实例中唯一。
    // 如果多个Proxy共享一个RpcClient，这个ID生成器也应该被合理管理。
    // 简单起见，我们假设每个Proxy有自己的ID序列或都用一个全局的。
    // 如果 RpcClient 中也用了 AtomicInteger，需要协调，这里为了演示清晰，Proxy也用一个。
    // 更好的做法是请求ID由 RpcClient 内部生成并填充到 RpcMessage 中。
    private static final AtomicLong REQUEST_ID = new AtomicLong(0);

    // 请求超时时间，单位毫秒
    private final long requestTimeoutMillis;

    // 使用ServiceDiscovery
    private final ServiceDiscovery serviceDiscovery;
    // 缓存服务地址和对应的RpcClient，避免每次请求都重新创建RpcClient，键为ip:port
    // 对于同一个服务地址，可以复用RpcClient和底层的Netty连接
    private final Map<String, RpcClient> rpcClientCache = new ConcurrentHashMap<>();
    // 缓存服务名和对应的服务地址列表
    private final Map<String, List<InetSocketAddress>> serviceAddressCache = new ConcurrentHashMap<>();
    // 负载均衡策略
    private final LoadBalanceStrategy loadBalanceStrategy;

    /**
     * 构造函数，使用默认序列化算法和默认超时时间
     */
    public RpcClientProxy(String zkDiscoveryAddress, long requestTimeoutMillis) {
        this(zkDiscoveryAddress,  SerializerFactory.getDefaultSerializer().getSerializerAlgorithm(),
                requestTimeoutMillis, new RoundRobinLoadBalance());  // 使用默认序列化算法和默认负载均衡策略
    }

    // 暂时不指定序列化算法使用默认
    public RpcClientProxy(String zkDiscoveryAddress, byte serializerAlgorithm, long requestTimeoutMillis, LoadBalanceStrategy loadBalanceStrategy) {
        if (zkDiscoveryAddress == null) {
            throw new RpcRegistryException("[RpcClientProxy] " + RpcErrorMessages.format(RpcErrorMessages.NULL_ZK_ADDRESS));
        }
        this.serviceDiscovery = new ZookeeperServiceDiscovery(zkDiscoveryAddress);
        this.serializerAlgorithm = serializerAlgorithm;
        this.requestTimeoutMillis = requestTimeoutMillis > 0 ? requestTimeoutMillis : 5000L;  // 保证超时 > 0
        this.loadBalanceStrategy = loadBalanceStrategy != null ? loadBalanceStrategy : new RoundRobinLoadBalance();

        logger.info("[RpcClientProxy] 初始化了服务发现 {}, 采用负载均衡 {}",
                serviceDiscovery.getClass().getName(), loadBalanceStrategy.getClass().getSimpleName());
    }

    //  创建代理对象，在调用对应方法时，会自动调用下面的invoke方法
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> serviceInterface) {
        // 首次为某服务接口创建代理对象时，可尝试订阅服务地址变化
        // 确保serviceInterface是接口
        if (!serviceInterface.isInterface()) {
            throw new RpcInvokeException("[RpcClientProxy] " + RpcErrorMessages.format(RpcErrorMessages.SERVICE_NOT_AN_INTERFACE));
//            throw new IllegalArgumentException("RpcClientProxy: serviceInterface 必须是一个接口");
        }

        // 获得该服务的服务名称
        String serviceName = serviceInterface.getName();
        // 订阅服务地址变化，当服务地址列表发生变化时，更新本地缓存
        this.serviceDiscovery.subscribe(serviceName, (name, newInstances) -> {
            logger.info("[RpcClientProxy] 服务 {} 的服务地址列表发生变化 {}，更新本地缓存", name, newInstances);
            serviceAddressCache.put(name, newInstances);
        });
        // 使用Java动态代理创建接口的代理对象
        // 当调用代理对象的方法时，会执行当前 RpcClientProxy 实例的 invoke 方法
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),  // 使用被代理类的类加载器
                new Class<?>[]{serviceInterface},   // 声明代理要实现的接口
                this);                   // 指定InvocationHandler为当前RpcClientProxy实例
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 获取被调用的接口的名称
        String serviceName = method.getDeclaringClass().getName();

        // 1. 从本地缓存或服务发现中获取服务实例地址
        List<InetSocketAddress> availableServiceInstances = serviceAddressCache.get(serviceName);
        if (availableServiceInstances == null || availableServiceInstances.isEmpty()) {
            logger.info("[RpcClientProxy] 从本地缓存中获取服务 {} 的实例列表失败，开始从服务发现中获取", serviceName);
            availableServiceInstances = serviceDiscovery.discoverService(serviceName);
            if (availableServiceInstances == null || availableServiceInstances.isEmpty()) {
                logger.warn("[RpcClientProxy] 从服务发现中获取服务 {} 的实例列表失败", serviceName);
            }
            // 将服务实例地址缓存起来
            serviceAddressCache.put(serviceName, availableServiceInstances);
        }
        logger.info("RpcClientProxy: 从服务发现中获取服务 {} 的实例列表成功, 服务实例列表为 {}",
                serviceName, availableServiceInstances);

        // 2. 使用负载均衡获得服务实例地址
        InetSocketAddress selectedInstanceAddress = loadBalanceStrategy.select(serviceName, availableServiceInstances);
        if (selectedInstanceAddress == null) {
            logger.error("[RpcClientProxy] 从服务发现中获取服务 {} 的实例列表成功, 但是没有可用的服务实例", serviceName);
            throw new RpcServiceNotFoundException("[RpcClientProxy] " + RpcErrorMessages.format(RpcErrorMessages.RPC_SERVICE_NOT_FOUND, serviceName));
//            throw new RegistryException("RpcClientProxy: 从服务发现中获取服务" + serviceName + "的实例列表成功, 但是没有可用的服务实例");
        }

        // 3. 获取或创建到选定地址的RpcClient实例
        RpcClient rpcClient = getOrCreateRpcClient(selectedInstanceAddress);

        // 4. 创建RpcRequest对象
        RpcMessage<RpcRequest> requestMessage = getRpcRequestRpcMessage(method, args);
        logger.info("[RpcClientProxy] 创建RpcMessage对象成功，开始发送请求，具体消息内容为：{}", requestMessage);

        // 5. 调用rpcClient发送RpcMessage对象，并获得一个CompletableFuture对象
        CompletableFuture<RpcMessage> responseFuture = rpcClient.sendRequest(requestMessage);
        logger.info("[RpcClientProxy] 调用sendRequest发送请求, 得到responseFuture: {}", responseFuture);

        // 4. 等待异步响应结果（只需要解决response类型的响应，pong类型在心跳处理器中已经提前消耗）
        RpcMessage responseMessage = null;
        try {
            logger.info("[RpcClientProxy] 阻塞当前线程，等待请求完成或超时");
            responseMessage = responseFuture.get(requestTimeoutMillis, TimeUnit.MILLISECONDS);
            logger.info("[RpcClientProxy] 请求完成，开始处理响应");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            RpcClient.PENDING_RPC_FUTURES.remove(requestMessage.getHeader().getRequestID());
            logger.warn("[RpcClientProxy] 请求被中断");
            throw new RpcNetworkException("[RpcClientProxy] RPC 请求 (ID: " + requestMessage.getHeader().getRequestID() +
                    ") 被打断当等待响应时.", e);
        } catch(ExecutionException e) {
            RpcClient.PENDING_RPC_FUTURES.remove(requestMessage.getHeader().getRequestID());
            Throwable cause = e.getCause();
            if (cause instanceof RpcException) {
                // 如果原始异常就是RpcException，直接抛出
                throw cause;
            }
            // 其他异常，包装成RpcException抛出
            throw new RpcInvokeException("[RpcClientProxy] RPC request (ID: " + requestMessage.getHeader().getRequestID() +
                    ") execution failed.", cause);
        } catch(TimeoutException e) {
            RpcClient.PENDING_RPC_FUTURES.remove(requestMessage.getHeader().getRequestID());
            throw new RpcTimeoutException("RPC request (ID: " + requestMessage.getHeader().getRequestID() +
                    ") timed out after " + this.requestTimeoutMillis + " ms.", e);
        }

        // 5. 处理响应的对象
        if (responseMessage == null) {
            throw new RpcInvokeException("[RpcClientProxy] " +
                    RpcErrorMessages.format(RpcErrorMessages.NULL_RPC_MESSAGE, requestMessage.getHeader().getRequestID()));
        }

        // 6. 检验获取对象的消息类型
        if (responseMessage.getHeader().getMsgType() != RpcProtocolConstant.MSG_TYPE_RESPONSE) {
            throw new RpcInvokeException("[RpcClientProxy] " + RpcErrorMessages.format(RpcErrorMessages.UNEXPECTED_MESSAGE_TYPE, responseMessage.getHeader().getMsgType()));
        }

        // 7. 检验对象返回ID
        if (responseMessage.getHeader().getRequestID() != requestMessage.getHeader().getRequestID()) {
            throw new RpcInvokeException("[RpcClientProxy] " + RpcErrorMessages.format(RpcErrorMessages.UNMATCHED_RESPONSE_ID,
                    responseMessage.getHeader().getRequestID(), requestMessage.getHeader().getRequestID()));
        }

        // 8. 从响应中提取响应体
        RpcResponse response = (RpcResponse) responseMessage.getBody();
        if (response == null) {
            throw new RpcInvokeException("[RpcClientProxy] " +
                    RpcErrorMessages.format(RpcErrorMessages.NULL_RPC_BODY, requestMessage.getHeader().getRequestID()));
        }
        if (response.getException() != null) {
            throw response.getException();
        }

        // 9.返回响应结果
        return response.getResult();
    }

    /**
     * 创建RpcRequest对象
     * @param method
     * @param args
     * @return
     */
    private RpcMessage<RpcRequest> getRpcRequestRpcMessage(Method method, Object[] args) {
        RpcRequest request = new RpcRequest(
                String.valueOf(REQUEST_ID.getAndIncrement()),
                method.getDeclaringClass().getName(),
                method.getName(),
                method.getParameterTypes(),
                args);

        // 2. 封装为 RpcMessage
        RpcMessage<RpcRequest> requestMessage = new RpcMessage<>(
                new MessageHeader(
                        RpcProtocolConstant.MAGIC_NUMBER,
                        RpcProtocolConstant.VERSION,
                        this.serializerAlgorithm,
                        RpcProtocolConstant.MSG_TYPE_REQUEST,
                        RpcProtocolConstant.STATUS_SUCCESS,
                        Long.parseLong(request.getRequestId())
                ),
                request
        );
        return requestMessage;
    }

    private RpcClient getOrCreateRpcClient(InetSocketAddress selectedInstanceAddress) {
        String addressKey = RpcFrameworkUtils.formatAddress(selectedInstanceAddress);
        // 双重检查锁定模式 (Double-Checked Locking) 创建单例 RpcClient (针对每个地址)
        // 1.从缓存中获取RpcClient
        RpcClient rpcClient = rpcClientCache.get(addressKey);
        if (rpcClient != null &&  rpcClient.getChannel() != null && rpcClient.getChannel().isActive()) {
            // 已经有一个可用的RpcClient实例
            logger.info("[RpcClientProxy] RpcClient已经连接到 {}", addressKey);
            return rpcClient;
        }
        if (rpcClient == null) {
            synchronized (rpcClientCache) { // 锁住缓存对象，而不是整个方法，减小锁粒度
                rpcClient  = rpcClientCache.get(addressKey); // 再次尝试从缓存中获取，防止其他方法已创建
                if (rpcClient != null &&  rpcClient.getChannel() != null && rpcClient.getChannel().isActive()) {
                    // 已经有一个可用的RpcClient实例
                    logger.info("[RpcClientProxy] 已经有其他服务创建并激活RpcClient实例到 {}", addressKey);
                    return rpcClient;
                }

                if (rpcClient != null) {
                    // 如果之前服务创建的实例仍然存在，但已失活，关闭它并，清除缓存
                    logger.info("[RpcClientProxy] 缓存中已存在RpcClient实例，但已失活，清除缓存");
                    rpcClient.close();
                    rpcClientCache.remove(addressKey);
                }

                logger.info("[RpcClientProxy] 为 {} 创建新的RpcClient实例", addressKey);
                // 2. 创建RpcClient
                rpcClient = new RpcClient(selectedInstanceAddress.getHostName(), selectedInstanceAddress.getPort());
                try {
                    rpcClient.connect(); // 创建连接
                    if (rpcClient.getChannel() != null && rpcClient.getChannel().isActive()) {
                        rpcClientCache.put(addressKey, rpcClient); // 缓存RpcClient
                        // 当这个RpcClient实例被关闭时，需要从缓存中移除
                        RpcClient clientToRemoveOnDisconnect = rpcClient;
                        rpcClient.getChannel().closeFuture().addListener((ChannelFutureListener) future -> {
                            logger.info("[RpcClientProxy] 关闭连接 {}, 从rpcClientCache中清除缓存", clientToRemoveOnDisconnect.getChannel());
                            // 条件移除：只有当缓存中的确实是这个将要被移除的client实例时才移除
                            // 这是为了防止在短时间内，一个旧的已关闭的client的closeFuture触发，
                            // 而此时缓存中可能已经是一个新的、同地址的client实例。
                            rpcClientCache.remove(addressKey, clientToRemoveOnDisconnect);  // 当缓存中确实有这个rpcClient时才移除
                        });
                    } else {
                        // 连接失败，不放入缓存，并关闭这个刚创建的client以释放资源
                        rpcClient.close();
                        throw new RpcNetworkException("[RpcClientProxy] " +
                                RpcErrorMessages.format(RpcErrorMessages.CONNECT_FAILED, addressKey));
                    }
                } catch(Exception e) {
                    rpcClient.close();
                    throw e;
                }
            }
        }
        return rpcClient;
    }

    /**
     * 关闭所有缓存的 RpcClient 和 ServiceDiscovery。
     */
    public void shutdown() {
        logger.info("[RpcClientProxy] 开始停止客户端...");
        if (this.serviceDiscovery != null) {
            this.serviceDiscovery.close();
            logger.info("[RpcClientProxy] 关闭serviceDiscovery");
        }
        if (!this.rpcClientCache.isEmpty()) {
            logger.info("[RpcClientProxy] 正在关闭 {} 个RpcClient实例", rpcClientCache.size());
            // 进行备份，避免迭代过程中出现 ConcurrentModificationException
            List<RpcClient> clientsToClose = new ArrayList<>(rpcClientCache.values());
            rpcClientCache.clear(); // 清理缓存，防止有新的请求进来
            for (RpcClient client : clientsToClose) {
                try {
                    logger.info("[RpcClientProxy] 关闭RpcClient实例 {}:{}", client.getHost(), client.getPort());
                    client.close();
                } catch (Exception e) {
                    logger.error("[RpcClientProxy] 错误关闭RpcClient实例 {}:{}", client.getHost(), client.getPort(), e);
                }
            }
        }
        logger.info("[RpcClientProxy] 关闭完成");
    }
}
