package com.lxy.rpc.core.client;

import com.lxy.rpc.core.common.exception.RpcException;
import com.lxy.rpc.core.protocol.RpcMessage;
import com.lxy.rpc.core.protocol.RpcProtocolConstant;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * 负责处理服务端返回的响应消息
 */
public class RpcClientHandler extends SimpleChannelInboundHandler<RpcMessage> {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(RpcClientHandler.class);

    /**
     * 当从服务端接收到数据，并且数据被Pipeline中前面的解码器成功解码为 RpcMessage 对象后，Netty会调用该方法
     * @param ctx 获取上下文信息
     * @param responseMessage 接收到的响应消息
     * @throws Exception 抛出异常
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage responseMessage) throws Exception {
        long requestId = responseMessage.getHeader().getRequestID();
        logger.info("[RpcClientHandler] received message from server {}: [Type={}, ReqID={}]",
                ctx.channel().remoteAddress(), responseMessage.getHeader().getMsgType(), requestId);

        // 根据消息类型进行处理
        if (responseMessage.getHeader().getMsgType() == RpcProtocolConstant.MSG_TYPE_RESPONSE) {
            // 从缓存中获取对应的CompletableFuture
            CompletableFuture<RpcMessage> future = RpcClient.PENDING_RPC_FUTURES.remove(requestId);

            if (future != null) {
                // 如果future不为空，说明有在等待返回响应的future
                // 设置future的返回结果
                future.complete(responseMessage);
                logger.info("[RpcClientHandler] completed future for request ID: {}", requestId);
            } else {
                // 如果future为空，说明已经超时了，直接打印异常
                logger.error("[RpcClientHandler] received response for request ID: {} but the future is already removed from the cache", requestId);
            }
        } else if (responseMessage.getHeader().getMsgType() == RpcProtocolConstant.MSG_TYPE_HEARTBEAT_PONG) {
            logger.debug("[RpcClientHandler] received heartbeat response from server {}",
                    ctx.channel().remoteAddress());
        } else {
            logger.error("[RpcClientHandler] received unknown message type from server {}",
                    ctx.channel().remoteAddress());
        }
    }

    /**
     * 当ChannelPipeline中发生异常时，Netty会调用这个方法。
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("RpcClientHandler caught exception: {}", cause.getMessage());
        // 当发生未捕获的异常时，通常意味着连接可能已损坏或处于不一致状态。
        // 一个常见的做法是关闭连接。
        // 同时，需要通知所有正在等待响应的请求它们失败了。
        if (!RpcClient.PENDING_RPC_FUTURES.isEmpty()) {
            logger.error("[RpcClientHandler] completing {} pending futures with exception due to channel error.",
                    RpcClient.PENDING_RPC_FUTURES.size());
            RpcClient.PENDING_RPC_FUTURES.forEach((id, future) -> {
                if (!future.isDone()) {
                    future.completeExceptionally(new RpcException("RpcClientHandler caught exception: " + cause.getMessage()));
                }
            });
            RpcClient.PENDING_RPC_FUTURES.clear();
        }
        ctx.close();
    }

    /**
     * 当Channel从其EventLoop中注销并且无法再处理I/O时调用（通常是连接已断开）。
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("RpcClientHandler: Channel to {} became inactive (disconnected).",
                ctx.channel().remoteAddress());
        if (!RpcClient.PENDING_RPC_FUTURES.isEmpty()) {
            logger.debug("[RpcClientHandler] completing {} pending futures with exception due to channel inactivity.",
                    RpcClient.PENDING_RPC_FUTURES.size());
            RpcClient.PENDING_RPC_FUTURES.forEach((id, pendingFuture) -> {
                if (!pendingFuture.isDone()) {
                    pendingFuture.completeExceptionally(new RpcException("RpcClientHandler channel inactive"));
                }
            });
            RpcClient.PENDING_RPC_FUTURES.clear();
        }
        // 调用父类的实现，它可能会将事件传递给Pipeline中的下一个Handler（如果有的话）
        super.channelInactive(ctx);
    }

    /**
     * channelActive: 当Channel变为活跃状态（连接成功建立）时调用，可以用来发送初始化消息等。
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("RpcClientHandler: Channel to {} became active.",
                ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
}
