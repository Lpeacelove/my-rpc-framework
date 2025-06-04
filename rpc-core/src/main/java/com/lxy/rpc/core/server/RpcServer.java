package com.lxy.rpc.core.server;

import com.lxy.rpc.core.protocol.codec.RpcFrameDecoder;
import com.lxy.rpc.core.protocol.codec.RpcMessageDecoderNetty;
import com.lxy.rpc.core.protocol.codec.RpcMessageEncoderNetty;
import com.lxy.rpc.core.server.registry.LocalServiceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 启动服务，监听端口，接收请求
 */
public class RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    private final int port;
    private final LocalServiceRegistry serviceRegistry;
    private EventLoopGroup bossGroup;  // 负责接收客户端的连接请求
    private EventLoopGroup workerGroup;  // 负责处理已经连接的客户端的请求
    // todo 线程池相关内容
//    private final ExecutorService threadPools; // 使用线程池处理请求

    public RpcServer(int port) {
        this.port = port;
        this.serviceRegistry = new LocalServiceRegistry();
        // 创建一个固定大小的线程池，可以根据需要调整
//        this.threadPools = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    // 注册服务
//    public <T> void register(Class<T> serviceInterface, T serviceImpl) {
//        serviceRegistry.register(serviceInterface, serviceImpl);
//    }

    // 启动服务
    public void start() {
//        try(ServerSocket serverSocket = new ServerSocket(port)) {
//            logger.info("服务端: RPC 服务器启动，监听端口 {}", port);
//            while (true) {  // 持续监听
//                try {
//                    Socket clientSocket = serverSocket.accept();  // 阻塞等待客户端连接
//                    logger.info("服务端: 接收到客户端连接 " + clientSocket.getInetAddress());
//                    // 为每个客户端创建一个新的任务，并提交到线程池中处理
//                    threadPools.execute(new RpcRequestHandler(clientSocket, serviceRegistry));
//                } catch (IOException e) {
//                    logger.error("服务端: 错误连接客户端 ", e);
//                }
//            }
//        } catch (IOException e){
//            logger.error("服务端: 不能监听到端口" + port, e);
//            throw new RuntimeException("服务端: 启动失败");
//        } finally {
//            if (threadPools != null && !threadPools.isShutdown()) {
//                threadPools.shutdown();
//            }
//        }

        // 创建bossGroup，线程数设置为1个，只涉及accept的操作，非常快
        bossGroup = new NioEventLoopGroup(1);
        // 创建workerGroup，线程数设置为CPU核数*2，涉及读写操作，比较慢
        // 默认即为CPU核数*2
        workerGroup = new NioEventLoopGroup();
        // 创建一个 RpcRequestHandler 实例，它包含了实际的业务调用逻辑。
        // 这个实例会被传递给每个新连接的 RpcServerHandlerNetty。
        // 注意：如果 RpcRequestHandler 是有状态的，需要考虑线程安全问题。
        // 如果是无状态的（像我们目前这样，依赖传入的 LocalServiceRegistry），则可以共享。
        RpcRequestHandler requestHandler = new RpcRequestHandler(this.serviceRegistry);

        try {
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
                            pipeline.addLast("loggerHandler", new LoggingHandler(LogLevel.DEBUG));

                            // 入站
                            pipeline.addLast("frameDecoder", new RpcFrameDecoder());
                            pipeline.addLast("messageDecoder", new RpcMessageDecoderNetty());
                            // 出站
                            pipeline.addLast("messageEncoder", new RpcMessageEncoderNetty());
                            pipeline.addLast("serverHandler", new RpcServerHandlerNetty(requestHandler));
                        }
                    });

            // 绑定端口，开始接收进来的连接
            ChannelFuture channelFuture = serverBootstrap.bind(port);
            // 使用sync()等待绑定操作完成 (阻塞当前线程直到绑定成功或失败)
            channelFuture.sync();
            logger.info("服务端: RPC 服务器启动，监听端口 {}", port);
            // 获取服务器Channel，并等待它关闭 (这是一个阻塞操作)
            // 通常，服务器会一直运行，直到应用程序退出或显式关闭。
            // 调用 channelFuture.channel().closeFuture().sync() 会使当前线程阻塞在这里，
            // 直到服务器的Channel被关闭。
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            logger.error("服务端: 启动失败", e);
            throw new RuntimeException("服务端: 启动失败");
        } finally {
            // 当服务器最终关闭时（例如，通过外部信号或在main方法结束时），
            // 或者在启动过程中发生异常，需要优雅地关闭Netty的线程组。
            System.out.println("RPC Server (Netty) is shutting down...");
            shutdown();
        }
    }

    private void shutdown() {
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

}
