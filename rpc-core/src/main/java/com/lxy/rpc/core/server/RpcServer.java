package com.lxy.rpc.core.server;

import com.lxy.rpc.core.protocol.codec.RpcFrameDecoder;
import com.lxy.rpc.core.protocol.codec.RpcMessageDecoderNetty;
import com.lxy.rpc.core.protocol.codec.RpcMessageEncoderNetty;
import com.lxy.rpc.core.registry.LocalServiceRegistry;
import com.lxy.rpc.core.registry.ServiceRegistry;
import com.lxy.rpc.core.registry.zookeeper.ZookeeperServiceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

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

    public RpcServer(int port, LocalServiceRegistry localServiceRegistry, String zkAddress) {
        this.port = port;
        this.localServiceRegistry = localServiceRegistry;
        // 初始化zk服务注册发现
        if (zkAddress != null && !zkAddress.isEmpty()) {
            this.serviceRegistry = new ZookeeperServiceRegistry(zkAddress);
        } else {
            this.serviceRegistry = null;
            logger.warn("RpcServer: 未指定zk地址, 远程服务注册功能将不会生效");
        }
        // 获取本机地址用于注册
        try {
            this.serverAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.info("RpcServer: 获取本机地址失败, 将使用默认地址 127.0.0.1");
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
            System.out.println("RpcServer: 服务端启动");
            logger.info("RpcServer: --- Provider Application Starting ---");
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
                            System.out.println("RpcServer: 正在连接...");
                            ChannelPipeline pipeline = socketChannel.pipeline();  // 获取当前的Channel的Pipeline

                            // 入站
                            pipeline.addLast("loggerHandler", new LoggingHandler(LogLevel.DEBUG)); // 日志处理器
                            pipeline.addLast("frameDecoder", new RpcFrameDecoder());  // 帧解码器
                            pipeline.addLast("messageDecoder", new RpcMessageDecoderNetty());  // 消息解码器

                            // 出站
                            pipeline.addLast("messageEncoder", new RpcMessageEncoderNetty());  // 消息编码器
                            pipeline.addLast("serverHandler", new RpcServerHandlerNetty(requestHandler));  // 服务端处理器


                        }
                    });

            System.out.println("RpcServer: RpcServer开始监听端口： " + port);
            // 绑定端口，开始接收进来的连接
            ChannelFuture channelFuture = serverBootstrap.bind(port);
            // 使用sync()等待绑定操作完成 (阻塞当前线程直到绑定成功或失败)
            channelFuture.sync();
            System.out.println("RpcServer: RpcServer绑定端口成功");
            logger.info("服务端: RPC 服务器启动，监听端口 {}", port);
            // 服务器启动成功后，注册服务到zookeeper
            registerServiceToRegistry();
            // 获取服务器Channel，并等待它关闭 (这是一个阻塞操作)
            // 通常，服务器会一直运行，直到应用程序退出或显式关闭。
            // 调用 channelFuture.channel().closeFuture().sync() 会使当前线程阻塞在这里，
            // 直到服务器的Channel被关闭。
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            logger.error("服务端: 启动失败", e);
            throw new RuntimeException("服务端: 启动失败");
        } finally {
            // 在关闭服务器之前，注销服务（虽然会自动注销，但也可以手动注销）
            unregisterServiceFromRegistry();
            // 当服务器最终关闭时（例如，通过外部信号或在main方法结束时），
            // 或者在启动过程中发生异常，需要优雅地关闭Netty的线程组。
            System.out.println("RPC Server (Netty) is shutting down...");
            shutdown();
        }
    }



    private void shutdown() {
        System.out.println("RpcServer: 服务正在优雅地关闭...");
        // 优雅地关闭线程组
        if (bossGroup != null && !bossGroup.isShutdown()) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
            System.out.println("RPC Server BossGroup shut down.");
        }
        if (workerGroup != null && !workerGroup.isShutdown()) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
            System.out.println("RPC Server WorkerGroup shut down.");
        }
        System.out.println("RPC Server (Netty) shut down.");
    }


    /**
     * 注册服务器本地服务到注册中心
     */
    private void registerServiceToRegistry() {
        if (this.serviceRegistry == null) {
            logger.info("RpcServer: 没有外部注册中心，跳过注册本地服务到外部注册中心的步骤");
            return;
        }
        if (localServiceRegistry.isEmpty()) {
            logger.info("RpcServer: 本地没有服务需要注册");
            return;
        }
        InetSocketAddress currentInetSocketAddress = new InetSocketAddress(this.serverAddress, this.port);
        for (String serviceName : localServiceRegistry.getRegisteredServiceNames()) {
            try {
                serviceRegistry.registerService(serviceName, currentInetSocketAddress);
                logger.info("RpcServer: 成功注册本地服务 {} 到外部注册中心: {}", serviceName, currentInetSocketAddress);
            } catch (Exception e) {
                logger.error("RpcServer: 注册本地服务 {} 到外部注册中心: {} 失败",
                        serviceName, currentInetSocketAddress, e);
            }
        }

    }

    /**
     * 注销服务
     */
    private void unregisterServiceFromRegistry() {
        if (this.serviceRegistry == null || this.localServiceRegistry == null) {
            logger.info("RpcServer: 注销服务时发现，外部注册中心未注册服务或本地未注册服务");
            return;
        }
        InetSocketAddress currentInetSocketAddress = new InetSocketAddress(this.serverAddress, this.port);
        for (String serviceName : localServiceRegistry.getRegisteredServiceNames()) {
            try {
                serviceRegistry.unregisterService(serviceName, currentInetSocketAddress);
                logger.info("RpcServer: 成功从外部注册中心 {} 注销服务 {}", currentInetSocketAddress, serviceName);
            } catch (Exception e) {
                logger.error("RpcServer: 从外部注册中心 {} 注销服务 {} 失败", currentInetSocketAddress, serviceName, e);
            }
        }
    }


}
