package com.lxy.rpc.core.client.handler;

import com.lxy.rpc.core.protocol.MessageHeader;
import com.lxy.rpc.core.protocol.RpcMessage;
import com.lxy.rpc.core.protocol.RpcProtocolConstant;
import com.lxy.rpc.core.protocol.RpcRequest;
import com.lxy.rpc.core.serialization.SerializerFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端自定义心跳处理器
 * 1. 检测到写空闲时，向服务端发送PING
 * 2. 检测到读空闲时，关闭连接
 */
public class HeartbeatClientHandler extends ChannelInboundHandlerAdapter {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatClientHandler.class);

    // 心跳信息的序列号
    private static final AtomicLong PING_ID_GENERATOR = new AtomicLong(0);

    private long writerIdleTimeSeconds;
    private int maxReaderIdleBeforeDisconnect;
    private int readerIdleCount;

    public HeartbeatClientHandler(long writerIdleTimeSeconds, int maxReaderIdleBeforeDisconnect) {
        this.writerIdleTimeSeconds = writerIdleTimeSeconds;
        this.maxReaderIdleBeforeDisconnect = maxReaderIdleBeforeDisconnect;
    }

    /**
     * 检测到读空闲时，关闭连接; 检测到写空闲时，向服务端发送PING
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        logger.debug("[HeartbeatClientHandler] userEventTriggered called.");
        if (evt instanceof IdleStateEvent idleStateEvent) {
            IdleState idleState = idleStateEvent.state();
            switch (idleState) {
                case WRITER_IDLE:
                    // 发生了写空闲，发送PING
                    logger.debug("HeartbeatClientHandler: 发送PING");
                    sendPing(ctx);
                    break;
                case READER_IDLE:
                    readerIdleCount++;
                    logger.debug("HeartbeatClientHandler: 检测到读空闲 {}/{}", readerIdleCount, maxReaderIdleBeforeDisconnect);
                    if (readerIdleCount >= maxReaderIdleBeforeDisconnect) {
                        // 触发读空闲次数达到阈值，关闭连接
                        logger.debug("HeartbeatClientHandler: 触发读空闲次数达到阈值，关闭连接");
                        ctx.close();
                    }
                    break;
                default:
                    break;
            }
        } else {
            logger.debug("HeartbeatClientHandler: 检测到非IdleState事件");
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 发送PING
     * @param ctx
     */
    private void sendPing(ChannelHandlerContext ctx) {
        MessageHeader messageHeader = new MessageHeader(
                RpcProtocolConstant.MAGIC_NUMBER,
                RpcProtocolConstant.VERSION,
                SerializerFactory.getDefaultSerializer().getSerializerAlgorithm(),
                RpcProtocolConstant.MSG_TYPE_HEARTBEAT_PING,
                RpcProtocolConstant.STATUS_SUCCESS,
                PING_ID_GENERATOR.incrementAndGet()
        );
        messageHeader.setBodyLength(0);
        RpcMessage<RpcRequest> rpcMessage = new RpcMessage<>(messageHeader,null);
        logger.debug("[HeartbeatClientHandler] 发送PING");
        ctx.writeAndFlush(rpcMessage).addListener(future -> {
            if (future.isSuccess()) {
                logger.debug("HeartbeatClientHandler: 发送PING成功");
            } else {
                if (future.cause() != null) {
                    logger.debug("HeartbeatClientHandler: 错误信息: {}", future.cause().getMessage());
                }
                logger.debug("HeartbeatClientHandler: 发送PING失败");
            }
        });
    }

    /**
     * 读数据时，重置读空闲计数器
     * @param ctx 上下文处理器
     * @param msg 读取的数据
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        readerIdleCount = 0;
        // 如果读到的是PONG，暂时不需要后续的业务处理，可以直接在这里消耗
        boolean isPongAndConsumed = false;
        if (msg instanceof RpcMessage) {
            RpcMessage rpcMessage = (RpcMessage) msg;
            if (rpcMessage.getHeader().getMsgType() == RpcProtocolConstant.MSG_TYPE_HEARTBEAT_PONG) {
                logger.debug("HeartbeatClientHandler: 读到PONG");
                isPongAndConsumed = true;
            }

            if (!isPongAndConsumed) {
                // 读到的不是PONG，交给下一个处理器处理
                super.channelRead(ctx, msg);
            } else {
                // 读到的是PONG，不需要后续的业务处理
                logger.debug("HeartbeatClientHandler: 读到PONG，不需要后续的业务处理");
            }
        }
    }

    /**
     * 激活时，重置读空闲计数器
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        readerIdleCount = 0;
        super.channelActive(ctx);
    }

    /**
     * 断开时，关闭连接
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("HeartbeatClientHandler: 断开连接");
        super.channelInactive(ctx);
    }

    /**
     * 异常时，关闭连接
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("HeartbeatClientHandler: 断开连接，异常: {}", cause.getMessage());
        super.exceptionCaught(ctx, cause);
    }
}
