package com.lxy.rpc.core.server;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.protocol.MessageHeader;
import com.lxy.rpc.core.protocol.RpcMessage;
import com.lxy.rpc.core.protocol.RpcProtocolConstant;
import com.lxy.rpc.core.serialization.SerializerFactory;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * 处理客户端请求，调用业务逻辑，发送响应
 */
public class RpcServerHandlerNetty extends SimpleChannelInboundHandler<RpcMessage> {
    private final RpcRequestHandler requestHandler;

    public RpcServerHandlerNetty(RpcRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
        System.out.println("RpcServerHandlerNetty: 创建RpcServerHandlerNetty");
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
        System.out.println("RpcServerHandlerNetty: received message from client " + ctx.channel().remoteAddress() +
                ": [Type=" + requestMessage.getHeader().getMsgType() + ", ReqID=" + requestMessage.getHeader().getRequestID() + "]");
        // 初始化要返回给客户端的响应
        RpcMessage responseMessage = null;
        // 读取客户端请求体的内容
        RpcRequest rpcRequest = (RpcRequest) requestMessage.getBody();
        // 根据消息类型进行处理，得到响应消息
        // 如果是请求消息
        if (requestMessage.getHeader().getMsgType() == RpcProtocolConstant.MSG_TYPE_REQUEST) {
            // 如果请求体为空，则返回错误响应
            if (rpcRequest == null) {
                System.out.println("RpcServerHandlerNetty received empty request from client " +
                        ctx.channel().remoteAddress());
                // 初始化错误消息
                RpcResponse errorResponse = new RpcResponse();
                // 设置错误响应的错误状态
                errorResponse.setException(new RuntimeException("RpcServerHandlerNetty received empty request from client " +
                        ctx.channel().remoteAddress()));
                // 设置响应的请求ID
                errorResponse.setResponseId(rpcRequest.getRequestId());
                // 创建响应头
                MessageHeader responseHeader = new MessageHeader(
                        RpcProtocolConstant.MAGIC_NUMBER,
                        RpcProtocolConstant.VERSION,
                        SerializerFactory.getDefaultSerializer().getSerializerAlgorithm(),
                        RpcProtocolConstant.MSG_TYPE_RESPONSE,
                        RpcProtocolConstant.STATUS_FAIL,
                        Long.parseLong(rpcRequest.getRequestId()));
                // 创建响应消息
                responseMessage = new RpcMessage(responseHeader, errorResponse);
            } else { // 如果是正常请求消息
                System.out.println("RpcServerHandlerNetty: received request from client " +
                        ctx.channel().remoteAddress() + ": " + rpcRequest);
                // 将请求交给业务逻辑处理
                RpcResponse rpcResponse = requestHandler.handle(rpcRequest);
                // 创建响应消息
                MessageHeader responseHeader = new MessageHeader(
                        RpcProtocolConstant.MAGIC_NUMBER,
                        RpcProtocolConstant.VERSION,
                        SerializerFactory.getDefaultSerializer().getSerializerAlgorithm(),
                        RpcProtocolConstant.MSG_TYPE_RESPONSE,
                        RpcProtocolConstant.STATUS_SUCCESS,
                        Long.parseLong(rpcRequest.getRequestId()));
                responseMessage = new RpcMessage(responseHeader, rpcResponse);
                System.out.println("客户端: 接收到服务端返回的响应" + responseMessage);
            }
        } else if (requestMessage.getHeader().getMsgType() == RpcProtocolConstant.MSG_TYPE_HEARTBEAT_REQUEST) {
            // 处理心跳请求，暂时设置返回消息体为null
            System.out.println("RpcServerHandlerNetty received heartbeat request from client " +
                    ctx.channel().remoteAddress());
            MessageHeader responseHeader = new MessageHeader(
                    RpcProtocolConstant.MAGIC_NUMBER,
                    RpcProtocolConstant.VERSION,
                    SerializerFactory.getDefaultSerializer().getSerializerAlgorithm(),
                    RpcProtocolConstant.MSG_TYPE_HEARTBEAT_RESPONSE,
                    RpcProtocolConstant.STATUS_SUCCESS,
                    Long.parseLong(rpcRequest.getRequestId()));
            responseMessage = new RpcMessage(responseHeader, null);
        } else {
            // 未知消息类型
            System.out.println("RpcServerHandlerNetty received unknown message type from client "
                    + ctx.channel().remoteAddress());
            // 初始化错误消息
            RpcResponse errorResponse = new RpcResponse();
            errorResponse.setException(new RuntimeException("RpcServerHandlerNetty received unknown message type from client "
                    + ctx.channel().remoteAddress()));
            errorResponse.setResponseId(rpcRequest.getRequestId());
            MessageHeader responseHeader = new MessageHeader(RpcProtocolConstant.MAGIC_NUMBER,
                    RpcProtocolConstant.VERSION,
                    SerializerFactory.getDefaultSerializer().getSerializerAlgorithm(),
                    RpcProtocolConstant.MSG_TYPE_RESPONSE,
                    RpcProtocolConstant.STATUS_FAIL,
                    Long.parseLong(rpcRequest.getRequestId()));
            responseMessage = new RpcMessage(responseHeader, errorResponse);
        }
        // 发送响应消息
        if (responseMessage != null) {
            RpcMessage finalResponseMessage = responseMessage;
            System.out.println("RpcServerHandlerNetty: Preparing to send response. Channel active: " +
                    ctx.channel().isActive() + ", Channel open: " + ctx.channel().isOpen());
            // 添加监听器，当发送成功时，就会调用监听器中的方法，输出日志
            ctx.writeAndFlush(responseMessage).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    System.out.println("RpcServerHandlerNetty: successfully sent response/pong for request ID " +
                            finalResponseMessage.getHeader().getRequestID() + " to " + ctx.channel().remoteAddress());
                } else {
                    System.out.println("the failure of future: " + future.cause());
                    System.out.println("RpcServerHandlerNetty: failed to send response/pong for request ID " +
                            finalResponseMessage.getHeader().getRequestID() + " to " + ctx.channel().remoteAddress());
                }
            });
        }
    }

    /**
     * 捕获异常
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("RpcServerHandlerNetty caught exception: " + cause.getMessage());
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
        System.out.println("RpcServerHandlerNetty: Channel to " +
                ctx.channel().remoteAddress() + " became active.");
        super.channelActive(ctx);  // 调用父类实现，确保事件正确传播
        System.out.println("RpcServerHandlerNetty: 激活事件的父类已被实现");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("RpcServerHandlerNetty: Channel to " +
                ctx.channel().remoteAddress() +  " became inactive (disconnected).");
        super.channelInactive(ctx);
        System.out.println("RpcServerHandlerNetty: 失活事件的父类已被实现");
    }
}
