package com.lxy.rpc.core.client;

import com.lxy.rpc.core.config.RpcConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Channel提供者，负责管理和维护到不同地址的服务提供者的连接池
 * 单例模式
 */
public final class ChannelProvider {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(ChannelProvider.class);

    private static Bootstrap bootstrap;  // 客户端启动引导
    private static NioEventLoopGroup eventLoopGroup; // 全局共享的EventLoopGroup

    // 缓存连接池
    private static final Map<String, ChannelPool> channelPoolMap = new ConcurrentHashMap<>();

    // 静态代码块，初始化全局共享的启动引导和EventLoopGroup
    static {
        initializeBootstrap();
    }

    private ChannelProvider(){}

    private static void initializeBootstrap() {
        eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY,  true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, RpcConfig.getClientConnectTimeoutMillis());
        logger.info("初始化ChannelProvider成功");
    }

    /**
     * 根据服务地址获取一个 Channel
     * 若连接池存在，则从连接池中获取一个Channel，否则新建一个连接池并返回一个Channel
     * @param inetSocketAddress
     * @return
     */
    public static CompletableFuture<ChannelHolder> getChannel(InetSocketAddress inetSocketAddress) {
        String key = inetSocketAddress.toString().substring(1); // 去掉第一个字符，即：/
        ChannelPool pool = channelPoolMap.get(key);

        if (pool == null) {
            synchronized (channelPoolMap) {
                pool = channelPoolMap.get(key);
                if (pool == null) {
                    // 创建一个新的 FixedChannelPool
                    // maxConnections: 最大连接数
                    // maxPendingAcquires: 池满时，每个连接的最大等待任务数
                    int maxConnections = RpcConfig.getClientConnectMaxConnections();
                    int maxPendingAcquires = RpcConfig.getClientConnectMaxPendingAcquires();

                    // 为这个特定的地址创建一个Bootstrap的副本，并设置远程地址
                    Bootstrap poolBootstrap = bootstrap.clone().remoteAddress(inetSocketAddress);

                    pool = new FixedChannelPool(
                            poolBootstrap,
                            new RpcClientChannelPoolHandler(),
                            maxConnections,
                            maxPendingAcquires
                    );
                    channelPoolMap.put(key, pool);
                    logger.info("创建一个新的ChannelPool成功，key: {}, maxConnections: {}, maxPendingAcquires: {}", key, maxConnections, maxPendingAcquires);
                }
            }
        }
        final ChannelPool finalPool = pool;
        // 从连接池获取一个异步channel
        Future<Channel> future = pool.acquire();
        // 将Netty的Future转换为Java的CompletableFuture
        CompletableFuture<ChannelHolder> completableFuture = new CompletableFuture<>();
        future.addListener(f -> {
            if (f.isSuccess()) {
                Channel channel = future.getNow();
                completableFuture.complete(new ChannelHolder(channel, finalPool));
            } else {
                completableFuture.completeExceptionally(f.cause());
            }
        });
        return completableFuture;
    }

    /**
     * 关闭所有已创建的连接池和全局的EventLoopGroup
     */
    public static void shutdown() {
        logger.info("[ChannelProvider] 开始执行shutdown()方法以停止服务...");
        for (ChannelPool pool : channelPoolMap.values()) {
            try {
                pool.close();
            } catch (Exception e) {
                logger.error("[ChannelProvider] 停止服务时发生异常: ", e);
            }
        }
        channelPoolMap.clear();

        if (eventLoopGroup != null && !eventLoopGroup.isShutdown()) {
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
            logger.info("[ChannelProvider] 停止eventLoopGroup服务成功");
        }
        logger.info("[ChannelProvider] 停止服务成功");
    }
}
