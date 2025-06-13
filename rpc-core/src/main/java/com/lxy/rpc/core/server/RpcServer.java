package com.lxy.rpc.core.server;

import com.lxy.rpc.core.common.constant.RpcErrorMessages;
import com.lxy.rpc.core.common.exception.RpcException;
import com.lxy.rpc.core.config.RpcConfig;
import com.lxy.rpc.core.protocol.codec.RpcFrameDecoder;
import com.lxy.rpc.core.protocol.codec.RpcMessageDecoderNetty;
import com.lxy.rpc.core.protocol.codec.RpcMessageEncoderNetty;
import com.lxy.rpc.core.registry.LocalServiceRegistry;
import com.lxy.rpc.core.registry.ServiceRegistry;
import com.lxy.rpc.core.registry.zookeeper.ZookeeperServiceRegistry;
import com.lxy.rpc.core.server.handler.HeartbeatServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.*;

/**
 * 启动服务，监听端口，接收请求
 */
public class RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    private final int port;
    private final LocalServiceRegistry localServiceRegistry;
    private final ServiceRegistry serviceRegistry;
    private EventLoopGroup bossGroup;  // 负责接收客户端的连接请求
    private EventLoopGroup workerGroup;  // 负责处理已经连接的客户端的请求
    private String serverAddress; // 服务器自身地址
    private Channel serverChannel; // 用于保存服务端启动的Channel，便于后面的关闭

    public RpcServer(LocalServiceRegistry localServiceRegistry) {
        this.port = RpcConfig.getServerPort();
        this.localServiceRegistry = localServiceRegistry;
        String zkAddress = RpcConfig.getRegistryZookeeperAddress();
        // 初始化zk服务注册发现
        if (zkAddress != null && !zkAddress.isEmpty()) {
            this.serviceRegistry = new ZookeeperServiceRegistry(zkAddress);
        } else {
            this.serviceRegistry = null;
            logger.warn("[RpcServer] 未指定zk地址, 远程服务注册功能将不会生效");
        }
        // 获取本机地址用于注册
        try {
            this.serverAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.info("[RpcServer] 获取本机地址失败, 将使用默认地址 127.0.0.1");
            this.serverAddress = "127.0.0.1";
        }
    }

    // 启动服务
    public void start() {
        // 创建bossGroup，线程数设置为1个，只涉及accept的操作，非常快
        bossGroup = new NioEventLoopGroup(1);
        // 创建workerGroup，线程数设置为CPU核数*2，涉及读写操作，比较慢
        // 默认即为CPU核数*2
        workerGroup = new NioEventLoopGroup();
        // 创建一个 RpcRequestHandler 实例，它包含了实际的业务调用逻辑。
        // 这个实例会被传递给每个新连接的 RpcServerHandlerNetty。
        // 注意：如果 RpcRequestHandler 是有状态的，需要考虑线程安全问题。
        // 如果是无状态的（像我们目前这样，依赖传入的 LocalServiceRegistry），则可以共享。
        RpcRequestHandler requestHandler = new RpcRequestHandler(this.localServiceRegistry);

        try {
            logger.info("[RpcServer] 服务端启动中...");
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // option是针对服务端SocketChannel的配置
                    .option(ChannelOption.SO_BACKLOG, 128) // 设置队列大小，防止连接过多
                    // childOption是针对客户端SocketChannel的配置
                    .childOption(ChannelOption.SO_KEEPALIVE, true) //  保持长连接
                    // handler 是针对服务端SocketChannel的配置, 用于处理服务端启动过程中的一些事件。
                    .handler(new LoggingHandler(LogLevel.INFO))  //  日志
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();  // 获取当前的Channel的Pipeline

                            pipeline.addLast("loggerHandler", new LoggingHandler(LogLevel.DEBUG)); // 日志处理器

                            // 出站
                            pipeline.addLast("messageEncoder", new RpcMessageEncoderNetty());  // 消息编码器

                            // 入站
                            pipeline.addLast("frameDecoder", new RpcFrameDecoder());  // 帧解码器
                            pipeline.addLast("messageDecoder", new RpcMessageDecoderNetty());  // 消息解码器

                            // 心跳机制处理
                            long readerIdlerTimeSeconds = RpcConfig.getServerHeartbeatReadIdleTimeoutSeconds();
                            pipeline.addLast("idleStateHandler", new IdleStateHandler(readerIdlerTimeSeconds, 0, 60, TimeUnit.SECONDS));
                            int maxReaderIdleCounts = RpcConfig.getServerHeartbeatReadIdleCloseCount();
                            pipeline.addLast("heartbeatHandler", new HeartbeatServerHandler(maxReaderIdleCounts));

                            pipeline.addLast("serverHandler", new RpcServerHandlerNetty(requestHandler));  // 服务端处理器


                        }
                    });

            // 绑定端口，开始接收进来的连接
            ChannelFuture channelFuture = serverBootstrap.bind(port);
            this.serverChannel = channelFuture.channel();
            // 使用sync()等待绑定操作完成 (阻塞当前线程直到绑定成功或失败)
            channelFuture.sync();
            logger.info("[RpcServer] 服务端: RPC 服务器启动，监听端口 {}", port);
            // 服务器启动成功后，注册服务到zookeeper
            registerServiceToRegistry();
            // 获取服务器Channel，并等待它关闭 (这是一个阻塞操作)
            // 通常，服务器会一直运行，直到应用程序退出或显式关闭。
            // 调用 channelFuture.channel().closeFuture().sync() 会使当前线程阻塞在这里，
            // 直到服务器的Channel被关闭。
            this.serverChannel.closeFuture().sync();
        } catch (Exception e) {
            logger.error("[RpcServer] 服务端: 启动失败", e);
            throw new RpcException(RpcErrorMessages.format(RpcErrorMessages.SERVICE_START_FAILED, e.getMessage()));
        } finally {
            // 这个finally块，会在this.serverChannel.closeFuture().sync()解除阻塞之后执行
            // 在关闭服务器之前，注销服务（虽然会自动注销，但也可以手动注销）
            logger.info("[RpcServer] start() 方法中的 finally 块正在被执行...注销服务");
            //
            unregisterServiceFromRegistry();
            shutdownNettyEventLoops();
            if (serviceRegistry != null) {
                serviceRegistry.close();
            }
            // 当服务器最终关闭时（例如，通过外部信号或在main方法结束时），
            // 或者在启动过程中发生异常，需要优雅地关闭Netty的线程组。
            logger.info("[RpcServer] start() 方法中的 finally 块中优雅地关闭Netty线程组执行完毕");
        }
    }

    /**
     * 优雅地关闭Netty线程组
     */
    private void shutdownNettyEventLoops() {
        logger.info("[RpcServer] 正在关闭Netty线程组");
        boolean bossGroupShutdown = false;
        boolean workerGroupShutdown = false;

        // 关闭bossGroup
        if (bossGroup != null && !bossGroup.isShutdown()) {
            try {
                bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly(); // 给bossGroup一个优雅的关闭时间，超过这个时间后，会强制关闭
                bossGroupShutdown = true;
                logger.info("[RpcServer] BossGroup已成功关闭");
            } catch (Exception e) {
                logger.error("[RpcServer] BossGroup关闭失败", e);
            }
        } else {
            bossGroupShutdown = true;
            logger.info("[RpcServer] BossGroup已处于关闭状态");
        }

        // 关闭workerGroup
        if (workerGroup != null && !workerGroup.isShutdown()) {
            try {
                workerGroup.shutdownGracefully(0, 15, TimeUnit.SECONDS).syncUninterruptibly(); // 给workerGroup一个更长的优雅的关闭时间，超过这个时间后，会强制关闭
                workerGroupShutdown = true;
                logger.info("[RpcServer] WorkerGroup已成功关闭");
            } catch (Exception e) {
                logger.error("[RpcServer] WorkerGroup关闭失败", e);
            }
        } else {
            workerGroupShutdown = true;
            logger.info("[RpcServer] WorkerGroup已处于关闭状态");
        }

        if (bossGroupShutdown && workerGroupShutdown) {
            logger.info("[RpcServer] Netty线程组已全部关闭");
        } else {
            logger.warn("[RpcServer] 存在未关闭的Netty线程组");
        }
    }


    /**
     * 停止服务
     */
    public void shutdown() {
        logger.info("[RpcServer] 开始执行shutdown()方法以停止服务...");
        final long shutdownTimeoutSeconds = 5;  // 设置优雅关闭的超时时间为5秒

        // 1. 从服务中心注销服务(带超时)
        if (serviceRegistry != null) {
            logger.info("[RpcServer] 正在从注册中心注销服务..., 时间限制为 {} 秒", shutdownTimeoutSeconds);
            ExecutorService unregisterThread = Executors.newSingleThreadExecutor(
                    r -> new Thread(r, "RpcServerServiceUnregisterThread")); // 创建一个线程，并设置线程名称
            Future<?> unregisterFuture = unregisterThread.submit(this::unregisterServiceFromRegistry);  // 将任务提交给线程池执行
            try {
                unregisterFuture.get(shutdownTimeoutSeconds, TimeUnit.SECONDS);
                logger.info("[RpcServer] 从注册中心注销服务成功");
            } catch (TimeoutException e) {
                unregisterFuture.cancel(true);
                logger.warn("[RpcServer] 从注册中心注销服务超时，已取消任务");
            } catch (Exception e) {
                logger.error("[RpcServer] 从注册中心注销服务失败", e);
            } finally {
                unregisterThread.shutdownNow();   // 关闭线程池
            }
        }

        // 2. 关闭Netty服务器
        // 2.1 关闭服务器的channel监听，停止接收新的连接
        if (serverChannel != null && serverChannel.isOpen()) {
            logger.info("[RpcServer] 服务器的channel监听正在关闭...");
            try {
                serverChannel.close().syncUninterruptibly();
                logger.info("[RpcServer] 服务器的channel监听已关闭");
            } catch (Exception e) {
                logger.error("[RpcServer] 服务器的channel监听关闭失败", e);
            }
        }

        // 2.2 优雅地关闭netty线程组
        shutdownNettyEventLoops();

        // 3. 关闭与服务中心地连接
        if (serviceRegistry != null) {
            logger.info("[RpcServer] 正在关闭与注册中心的连接..., 时间限制为 {} 秒",  shutdownTimeoutSeconds);
            ExecutorService zkCloseThread = Executors.newSingleThreadExecutor(
                    r -> new Thread(r, "RpcServerZkCloseThread"));
            Future<?> zkCloseFuture = zkCloseThread.submit(() -> {
                try {
                    serviceRegistry.close();
                } catch (Exception e) {
                    logger.error("[RpcServer] 与注册中心的连接关闭失败", e);
                }
            });
            try {
                zkCloseFuture.get(shutdownTimeoutSeconds, TimeUnit.SECONDS);
                logger.info("[RpcServer] 与注册中心的连接已关闭");
            } catch (TimeoutException e){
                zkCloseFuture.cancel(true);
                logger.warn("[RpcServer] 与注册中心的连接关闭超时，已取消任务");
            } catch (Exception e) {
                logger.error("[RpcServer] 与注册中心的连接关闭失败", e);
            } finally {
                zkCloseThread.shutdownNow();
            }
        }

        logger.info("[RpcServer] shutdown()方法执行完毕");
    }


    /**
     * 注册服务器本地服务到注册中心
     */
    private void registerServiceToRegistry() {
        if (this.serviceRegistry == null) {
            logger.info("[RpcServer] 没有外部注册中心，跳过注册本地服务到外部注册中心的步骤");
            return;
        }
        if (localServiceRegistry.isEmpty()) {
            logger.info("[RpcServer] 本地没有服务需要注册");
            return;
        }
        InetSocketAddress currentInetSocketAddress = new InetSocketAddress(this.serverAddress, this.port);
        for (String serviceName : localServiceRegistry.getRegisteredServiceNames()) {
            try {
                serviceRegistry.registerService(serviceName, currentInetSocketAddress);
                logger.info("[RpcServer] 成功注册本地服务 {} 到外部注册中心: {}", serviceName, currentInetSocketAddress);
            } catch (Exception e) {
                logger.error("[RpcServer] 注册本地服务 {} 到外部注册中心: {} 失败",
                        serviceName, currentInetSocketAddress, e);
            }
        }

    }

    /**
     * 注销服务
     */
    private void unregisterServiceFromRegistry() {
        if (this.serviceRegistry == null || this.localServiceRegistry == null) {
            logger.info("[RpcServer] 注销服务时发现，外部注册中心未注册服务或本地未注册服务");
            return;
        }
        InetSocketAddress currentInetSocketAddress = new InetSocketAddress(this.serverAddress, this.port);
        for (String serviceName : localServiceRegistry.getRegisteredServiceNames()) {
            try {
                serviceRegistry.unregisterService(serviceName, currentInetSocketAddress);
                logger.info("[RpcServer] 成功从外部注册中心 {} 注销服务 {}", currentInetSocketAddress, serviceName);
            } catch (Exception e) {
                logger.error("[RpcServer] 从外部注册中心 {} 注销服务 {} 失败", currentInetSocketAddress, serviceName, e);
            }
        }
    }


}
