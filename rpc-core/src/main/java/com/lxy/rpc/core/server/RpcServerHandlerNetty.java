package com.lxy.rpc.core.server;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.common.constant.RpcErrorMessages;
import com.lxy.rpc.core.common.exception.RpcException;
import com.lxy.rpc.core.protocol.MessageHeader;
import com.lxy.rpc.core.protocol.RpcMessage;
import com.lxy.rpc.core.protocol.RpcProtocolConstant;
import com.lxy.rpc.core.serialization.SerializerFactory;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 处理客户端请求，调用业务逻辑，发送响应
 */
public class RpcServerHandlerNetty extends SimpleChannelInboundHandler<RpcMessage> {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(RpcServerHandlerNetty.class);
    private final RpcRequestHandler requestHandler;
    private final ExecutorService businessThreadPool;

    public RpcServerHandlerNetty(RpcRequestHandler requestHandler, ExecutorService businessThreadPool) {
        this.requestHandler = requestHandler;
        this.businessThreadPool = businessThreadPool;
    }

    /**
     * 当从客户端接收到数据，并且数据被Pipeline中前面的解码器成功解码为 RpcMessage 对象后，
     * Netty会调用这个方法。
     * @param ctx ChannelHandler的上下文
     * @param requestMessage 解码后的客户端请求 RpcMessage 对象
     * @throws Exception 处理过程中可能发生的异常
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage requestMessage) throws Exception {
        logger.info("[RpcServerHandlerNetty] received message from client {}: [Type={}, ReqID={}]",
                ctx.channel().remoteAddress(), requestMessage.getHeader().getMsgType(), requestMessage.getHeader().getRequestID());
        // 读取客户端请求体的内容
        RpcRequest rpcRequest = (RpcRequest) requestMessage.getBody();
        MessageHeader requestHeader = requestMessage.getHeader();

        // 处理PING类型请求
        if (requestMessage.getHeader().getMsgType() == RpcProtocolConstant.MSG_TYPE_HEARTBEAT_PING) {
            // 处理心跳请求，暂时设置返回消息体为null
            logger.info("[RpcServerHandlerNetty] received heartbeat request from client {}: {}",
                    ctx.channel().remoteAddress(), rpcRequest);
            MessageHeader responseHeader = new MessageHeader(
                    RpcProtocolConstant.MAGIC_NUMBER,
                    RpcProtocolConstant.VERSION,
                    requestHeader.getSerializerAlgorithm(),
                    RpcProtocolConstant.MSG_TYPE_HEARTBEAT_PONG,
                    RpcProtocolConstant.STATUS_SUCCESS,
                    requestHeader.getRequestID());
            writeResponseMessageToNetty(ctx, responseHeader, null);
        }

        // 处理非request和ping的其他类型
        if (requestMessage.getHeader().getMsgType() != RpcProtocolConstant.MSG_TYPE_REQUEST) {
            // 未知消息类型
            logger.warn("[RpcServerHandlerNetty] received unknown message type from client {}",
                    ctx.channel().remoteAddress());
            // 初始化错误消息
            RpcResponse errorResponse = new RpcResponse();
            errorResponse.setException(new RuntimeException("RpcServerHandlerNetty received unknown message type from client "
                    + ctx.channel().remoteAddress()));
            MessageHeader responseHeader = new MessageHeader(RpcProtocolConstant.MAGIC_NUMBER,
                    RpcProtocolConstant.VERSION,
                    requestHeader.getSerializerAlgorithm(),
                    RpcProtocolConstant.MSG_TYPE_RESPONSE,
                    RpcProtocolConstant.STATUS_FAIL,
                    requestHeader.getRequestID());
            writeResponseMessageToNetty(ctx, responseHeader, errorResponse);
        }

        // 根据消息类型进行处理，得到响应消息
        // 如果是请求消息
        if (requestMessage.getHeader().getMsgType() == RpcProtocolConstant.MSG_TYPE_REQUEST) {
            // 如果请求体为空，则返回错误响应
            if (rpcRequest == null) {
                logger.warn("[RpcServerHandlerNetty] received empty request from client {}",
                        ctx.channel().remoteAddress());
                // 初始化错误消息
                RpcResponse errorResponse = new RpcResponse();
                // 设置错误响应的错误状态
                errorResponse.setException(new RpcException(RpcErrorMessages.format(RpcErrorMessages.NULL_RPC_BODY, -1L)));
                // 创建响应头
                MessageHeader responseHeader = new MessageHeader(
                        RpcProtocolConstant.MAGIC_NUMBER,
                        RpcProtocolConstant.VERSION,
                        requestHeader.getSerializerAlgorithm(),
                        RpcProtocolConstant.MSG_TYPE_RESPONSE,
                        RpcProtocolConstant.STATUS_FAIL,
                        requestHeader.getRequestID());
                writeResponseMessageToNetty(ctx, responseHeader, errorResponse);
            } else { // 如果是正常请求消息
                logger.info("[RpcServerHandlerNetty] received request from client {}: {}",
                        ctx.channel().remoteAddress(), rpcRequest);
                // 将请求交给业务逻辑处理
                // 此处是业务逻辑处理，需要使用线程池处理
                CompletableFuture.supplyAsync(() -> {
                    logger.info("[RpcServerHandlerNetty] 在线程池中开始处理请求 from client {}: {}",
                            ctx.channel().remoteAddress(), rpcRequest);
                    return requestHandler.handle(rpcRequest);
                }, businessThreadPool)
                        .whenCompleteAsync((rpcResponse, throwable) -> {
                            final String threadName = Thread.currentThread().getName();
                            final RpcMessage<RpcResponse> responseMessage;
                            logger.info("[RpcServerHandlerNetty] 在线程池中处理完毕，准备返回结果 to client {}: {}",
                                    ctx.channel().remoteAddress(), rpcResponse);
                            // 如果处理过程中有异常，则返回异常信息，throwable 不为 null
                            if (throwable != null) {
                                logger.error("[RpcServerHandlerNetty] 处理过程中有异常，准备返回异常信息 to client {}: {}",
                                        ctx.channel().remoteAddress(), throwable.getMessage());
                                RpcResponse errorResponse = getErrorResponse(throwable);
                                MessageHeader responseHeader = new MessageHeader(
                                        RpcProtocolConstant.MAGIC_NUMBER,
                                        RpcProtocolConstant.VERSION,
                                        requestHeader.getSerializerAlgorithm(),
                                        RpcProtocolConstant.MSG_TYPE_RESPONSE,
                                        RpcProtocolConstant.STATUS_FAIL,
                                        requestHeader.getRequestID());
                                responseMessage = new RpcMessage<>(responseHeader, errorResponse);
                            } else {
                                MessageHeader responseHeader = new MessageHeader(
                                        RpcProtocolConstant.MAGIC_NUMBER,
                                        RpcProtocolConstant.VERSION,
                                        requestHeader.getSerializerAlgorithm(),
                                        RpcProtocolConstant.MSG_TYPE_RESPONSE,
                                        RpcProtocolConstant.STATUS_SUCCESS,
                                        requestHeader.getRequestID());
                                responseMessage = new RpcMessage<>(responseHeader, rpcResponse);
                            }
                            ctx.writeAndFlush(responseMessage).addListener((ChannelFutureListener) future -> {
                                if (future.isSuccess()) {
                                    logger.debug("[RpcServerHandlerNetty] successfully sent response/pong for request ID {} to {}",
                                            responseMessage.getHeader().getRequestID(), ctx.channel().remoteAddress());
                                } else {
                                    logger.error("[RpcServerHandlerNetty] failed to send response/pong for request ID {} to {}. Cause: {}",
                                            responseMessage.getHeader().getRequestID(), ctx.channel().remoteAddress(), future.cause().getMessage());
                                }
                            });
                        });
                logger.info("[RpcServerHandlerNetty] sent response/pong for request ID to {}",
                        ctx.channel().remoteAddress());
            }
        }
    }

    private static RpcResponse getErrorResponse(Throwable throwable) {
        RpcResponse errorResponse = new RpcResponse();
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (cause instanceof Exception) {
            errorResponse.setException((Exception) cause);
        } else {
            // 如果是 Error，可以封装为 RuntimeException 或其他 Exception 子类
            errorResponse.setException(new RuntimeException("Unexpected error: " + cause.getClass().getName(), cause));
        }
        return errorResponse;
    }

    public void writeResponseMessageToNetty(ChannelHandlerContext ctx, MessageHeader responseHeader, RpcResponse response) {
        final RpcMessage<RpcResponse> responseMessage = new RpcMessage<>(responseHeader, response);
        ctx.writeAndFlush(responseMessage).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                logger.debug("[RpcServerHandlerNetty] successfully sent response/pong for request ID {} to {}",
                        responseMessage.getHeader().getRequestID(), ctx.channel().remoteAddress());
            } else {
                logger.error("[RpcServerHandlerNetty] failed to send response/pong for request ID {} to {}. Cause: {}",
                        responseMessage.getHeader().getRequestID(), ctx.channel().remoteAddress(), future.cause().getMessage());
            }
        });
    }

    /**
     * 捕获异常
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("[RpcServerHandlerNetty] caught exception: {}", cause.getMessage());
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            // 关闭连接，如果关闭失败则记录
            ctx.close().addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    /**
     * 当一个新的客户端Channel连接到服务端并变为活跃状态时调用。
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);  // 调用父类实现，确保事件正确传播
        System.out.println("RpcServerHandlerNetty: 激活事件的父类已被实现");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }
}
