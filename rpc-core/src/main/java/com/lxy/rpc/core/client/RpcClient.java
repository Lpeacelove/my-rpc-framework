package com.lxy.rpc.core.client;

import com.lxy.rpc.core.common.exception.ProtocolException;
import com.lxy.rpc.core.common.exception.RpcException;
import com.lxy.rpc.core.protocol.RpcMessage;
import com.lxy.rpc.core.client.RpcClientHandler;
import com.lxy.rpc.core.protocol.codec.RpcFrameDecoder;
import com.lxy.rpc.core.protocol.codec.RpcMessageDecoderNetty;
import com.lxy.rpc.core.protocol.codec.RpcMessageEncoderNetty;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责建立连接、管理连接、发送请求
 * 1. 初始化Netty的事件循环组
 * 2. 配置Bootstrap
 * 3. 提供方法异步连接
 * 4. 提供方法发送异步请求到channel，并返回一个异步响应
 * 5. 创建一个map来保存异步响应和请求id的映射关系， 待服务端返回时，根据请求id找到对应的异步响应对象，并设置返回结果
 * 6. 关闭连接
 */
public class RpcClient {
    private final String host;
    private final int port;
    private final EventLoopGroup eventLoopGroup;  // Netty的事件循环组，处理所有Channel的I/O事件
    private final Bootstrap bootstrap;  // Netty客户端的启动引导类
    private volatile Channel channel;   // 当前客户端与服务端建立的连接通道，volatile保证可见性

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    // 用于存储<请求ID, CompletableFuture<RpcMessage>>的映射
    // 当收到响应时，RpcClientHandler会根据响应中的请求ID找到对应的Future并完成它。
    // 必须是static或者通过某种方式让RpcClientHandler能够访问到它。
    // 设为public static是为了简化示例，实际项目中可能有更好的管理方式（如依赖注入）。
    public static final Map<Long, CompletableFuture<RpcMessage>> PENDING_RPC_FUTURES  = new ConcurrentHashMap<>();

    public RpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        configureBootstrap();
    }

    /**
     * 配置Bootstrap
     */
    private void configureBootstrap() {
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                // ChannelInitializer是一个特殊的Handler，当一个新的Channel成功创建后，
                // 它的initChannel方法会被调用，用于向该Channel的Pipeline中添加其他Handler。
                .handler(new ChannelInitializer<SocketChannel>() {
                    /**
                     * 初始化Channel
                     *
                     * @param socketChannel 当前的Channel
                     * @throws Exception 抛出异常
                     */
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        // 1. 获取当前的Channel的Pipeline
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        // 2. 加入自带的日志处理器
                        pipeline.addLast("loggerHandler", new LoggingHandler(LogLevel.DEBUG));
                        // 3. 加入入站处理器
                        // 3.1 RpcFrameDecoder: 首先对接收到的字节流进行帧分割，解决粘包/半包问题。
                        pipeline.addLast("frameDecoder", new RpcFrameDecoder());
                        // 3.2 RpcMessageDecoder: 对RpcMessage进行解码，将字节流转为RpcMessage对象。
                        pipeline.addLast("messageDecoder", new RpcMessageDecoderNetty());
                        // 4.  添加出站处理器
                        // 4.1 RpcMessageEncoder: 对RpcMessage进行编码，将RpcMessage转为字节流。
                        pipeline.addLast("messageEncoder", new RpcMessageEncoderNetty());
                        // 4.2 RpcClientHandler: 客户端的业务逻辑处理器，处理解码后的RpcMessage响应。
                        pipeline.addLast("clientHandler", new RpcClientHandler());
                    }
                });
    }

    /**
     * 异步连接到服务端。
     * 使用 synchronized 确保在多线程环境下只有一个线程尝试建立或重用连接。
     */
    public synchronized void connect() {
        // 先判断该连接是否已经建立
        if (channel != null && channel.isActive()) {
            System.out.println("RpcClient is already connected to " + host + ":" + port);
            return;
        }

        try {
            // 调用bootstrap.connect()发起异步连接操作，返回一个ChannelFuture
            ChannelFuture channelFuture = bootstrap.connect(host, port);
            // 使用sync()方法等待连接操作完成。这是一个阻塞操作
            channelFuture.sync();

            if (channelFuture.isSuccess()) {
                this.channel = channelFuture.channel();
                System.out.println("RpcClient connected to " + host + ":" + port);
                // 为该channel设置一个监听器，当该channel关闭时，会触发该监听器并执行相应的操作。
                channel.closeFuture().addListener(future -> {
                    System.out.println("RpcClient disconnected from " + host + ":" + port);
                    // 关闭前，需要清除掉PENDING_RPC_FUTURES中的异步响应对象
                    // 首先获取所有的异步响应对象，然后判断是否已经完成，如果没有完成，则取消它们。
                    PENDING_RPC_FUTURES.forEach((id, pendingFuture) -> {
                        if (!pendingFuture.isDone()) {
                            pendingFuture.completeExceptionally(new ProtocolException("Connection closed before response for request ID: " + id));
                        }
                    });
                    // 最后，清空PENDING_RPC_FUTURES，确保下次连接时，不会残留旧的异步响应对象。
                    PENDING_RPC_FUTURES.clear();
                });
                this.channel = null; // 重置channel,  确保下次连接时，不会残留旧的channel。
            } else {
                System.err.println("RpcClient failed to connect to " +
                        host + ":" + port + ". Cause: " + channelFuture.cause().getMessage());
                channelFuture.cause().printStackTrace();
            }

        } catch (Exception e) {
            System.err.println("RpcClient failed to connect to " +
                    host + ":" + port + ". Cause: " + e.getMessage());
            e.printStackTrace();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // 重新设置中断状态
            }
            throw new RuntimeException("RpcClient failed to connect to " +
                    host + ":" + port + ". Cause: " + e.getMessage(), e);
        }
    }

    /**
     * 发送请求
     * @param message 需要发送的请求
     * @return 一个CompletableFuture，用于异步获取服务端响应的RpcMessage
     */
    public CompletableFuture<RpcMessage> sendRequest(RpcMessage message) {
        // 判断当前channel状态
        if (channel == null || !channel.isActive()) {
            System.err.println("RpcClient is not connected. Cannot send request ID: " + message.getHeader().getRequestID());
            // 返回一个错误的异步结果
            return CompletableFuture.failedFuture(new RpcException("Client not connected. Please connect first for request ID: "
                    + message.getHeader().getRequestID()));
        }

        // 创建一个CompletableFuture对象，用于保存异步响应结果
        CompletableFuture<RpcMessage> future = new CompletableFuture<>();
        PENDING_RPC_FUTURES.put(message.getHeader().getRequestID(), future);

        System.out.println("RpcClient sending request ID: " +
                message.getHeader().getRequestID() + " to " + this.channel.remoteAddress());

        // 通过Channel异步地写入并冲刷RpcMessage对象
        // RpcMessage会依次经过Pipeline中的RpcMessageEncoderNetty进行编码
        this.channel.writeAndFlush(message).addListener((ChannelFutureListener) channelFuture -> {
            if (!channelFuture.isSuccess()) {
                // 发送失败
                System.err.println("RpcClient failed to send request ID: " +
                        message.getHeader().getRequestID() + " to " + this.channel.remoteAddress());
                channelFuture.cause().printStackTrace();
                // 移除该异步响应对象，并设置一个失败的CompletableFuture
                PENDING_RPC_FUTURES.remove(message.getHeader().getRequestID());
                future.completeExceptionally(channelFuture.cause());
            } else {
                // 发送成功
                System.out.println("RpcClient sent request ID: " +
                        message.getHeader().getRequestID() + " to " + this.channel.remoteAddress());
            }
        });
        return future;
    }

    /**
     * 关闭连接
     */
    public void close() {
        if (channel != null && channel.isOpen()) {
            try {
                System.out.println("RpcClient closing connection to " + host + ":" + port);
                channel.close().syncUninterruptibly(); // 关闭连接，并等待关闭操作完成
            } catch (Exception e) {
                System.err.println("RpcClient failed to close connection to " +
                        host + ":" + port + ". Cause: " + e.getMessage());
            }
        }
        if (eventLoopGroup != null && !eventLoopGroup.isShutdown()) {
            eventLoopGroup.shutdownGracefully().syncUninterruptibly(); // 关闭EventLoopGroup，并等待关闭操作完成
            System.out.println("RpcClient closed connection to " + host + ":" + port);
        }
        // 清除还未完成的RPC请求，并设置一个失败的CompletableFuture
        PENDING_RPC_FUTURES.forEach((id, pendingFuture) -> {
            if (!pendingFuture.isDone()) {
                pendingFuture.completeExceptionally(new ProtocolException("Connection closed before response for request ID: " + id));
            }
        });
        PENDING_RPC_FUTURES.clear();
        System.out.println("RpcClient closed.");
    }
}
