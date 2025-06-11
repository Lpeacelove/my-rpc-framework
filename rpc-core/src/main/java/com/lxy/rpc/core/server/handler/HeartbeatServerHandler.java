package com.lxy.rpc.core.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 心跳处理
 * 1. 继承  ChannelInboundHandlerAdapter 并重写 userEventTriggered 方法来处理 IdleStateEvent事件
 * 2. 也可以处理入站的ping消息，（但这里ping主要由客户端的写空闲触发，服务端主要处理读空闲）
 *    主要用来响应 IdleStateHandler 触发的读空闲事件
 *    也可以处理客户端发来的 PING 请求，并回复 PONG 响应
 */
public class HeartbeatServerHandler extends ChannelInboundHandlerAdapter {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatServerHandler.class);

    // 读空闲处理的最大次数
    private final int maxReaderIdleCount;
    // 读空闲处理的次数
    private int readIdleCount = 0;

    public HeartbeatServerHandler(int maxReaderIdleCount) {
        this.maxReaderIdleCount = maxReaderIdleCount;
    }

    /**
     * 当 IdleStateHandler 检测到空闲并超时时会触发该方法
     * @param ctx 上下文处理器
     * @param evt 检测到的事件
     * @throws Exception 抛出异常
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        System.out.println("[HeartbeatServerHandler] userEventTriggered called. Channel: " + ctx.channel().remoteAddress() +
                ", Event Object: " + evt +
                ", Event Class: " + (evt != null ? evt.getClass().getName() : "null"));
        if (evt instanceof IdleStateEvent idleStateEvent) {
            IdleState idleState = idleStateEvent.state();
            if (idleState == IdleState.READER_IDLE) {
                readIdleCount++;
                logger.debug("[HeartbeatServerHandler] channel: {} 诱发读空闲: {}/{}",
                        ctx.channel().remoteAddress(), readIdleCount, maxReaderIdleCount);
                if (readIdleCount >= maxReaderIdleCount) {
                    logger.warn("[HeartbeatServerHandler] 读空闲超时 {} 次, 关闭连接, {}",
                            readIdleCount, ctx.channel().remoteAddress());
                    ctx.close().addListener(future -> {
                        if (future.isSuccess()) {
                            logger.info("[HeartbeatServerHandler] 关闭连接成功 {}", ctx.channel().remoteAddress());
                        } else {
                            logger.info("[HeartbeatServerHandler] 关闭连接失败 {}", ctx.channel().remoteAddress());
                        }
                    });
                }
            } else if (idleState == IdleState.WRITER_IDLE) {
                // 服务端通常不主动发送心跳给客户端，除非双向心跳设计
                // 可以记录日志，但不处理
                logger.debug("[HeartbeatServerHandler] channel: {} 诱发写空闲", ctx.channel().remoteAddress());
            } else if (idleState == IdleState.ALL_IDLE) {
                // 暂不处理，没有必要
                logger.debug("[HeartbeatServerHandler] channel: {} 诱发读写空闲", ctx.channel().remoteAddress());
            }
        } else {
            logger.debug("[HeartbeatServerHandler] channel: {} 触发其他事件",
                    ctx.channel().remoteAddress());
            // 如果不是 IdleStateEvent，则调用下一个 Handler
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 当 channel 上有任何数据可读的时候，就会触发该方法 ，重置读空闲计数器
     * @param ctx 上下文处理器
     * @param msg 消息
     * @throws Exception 抛出异常
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        readIdleCount = 0;
        logger.debug("[HeartbeatServerHandler] channel, {} 重置读空闲", ctx.channel().remoteAddress());
        super.channelRead(ctx, msg);
    }

    /**
     * 当 channel 处于活动状态的时候，就会触发该方法
     * @param ctx 上下文处理器
     * @throws Exception 抛出异常
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("[HeartbeatServerHandler] channel, {} 处于活动状态", ctx.channel().remoteAddress());
        // 重置读空闲计数器
        readIdleCount = 0;
        super.channelActive(ctx);
    }

    /**
     * 当 channel 处于非活动状态的时候，就会触发该方法
     * @param ctx 上下文处理器
     * @throws Exception 抛出异常
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("[HeartbeatServerHandler] channel, {} 处于非活动状态", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    /**
     * 当 channel 抛出异常的时候，就会触发该方法
     * @param ctx 上下文处理器
     * @param cause 异常
     * @throws Exception 抛出异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("[HeartbeatServerHandler] channel, {} 抛出异常", ctx.channel().remoteAddress(), cause);
        // 出现异常，可以抛给下一个处理器，或者简单地直接关闭连接
        super.exceptionCaught(ctx, cause);
    }
}
