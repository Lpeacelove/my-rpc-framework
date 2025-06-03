package com.lxy.rpc.core.protocol.codec;

import com.lxy.rpc.core.common.constant.MessageConstant;
import com.lxy.rpc.core.common.exception.ProtocolException;
import com.lxy.rpc.core.protocol.RpcMessage;
import com.lxy.rpc.core.protocol.RpcProtocolConstant;
import com.lxy.rpc.core.serialization.Serializer;
import com.lxy.rpc.core.serialization.SerializerFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class RpcMessageEncoderNetty extends MessageToByteEncoder<RpcMessage> {
    /**
     * Netty会调用这个方法来将 RpcMessage 对象编码成字节流。
     * @param channelHandlerContext 为ChannelHandler提供上下文，可以获取到Channel，ChannelPipeline，ChannelHandler等
     * @param rpcMessage 需要编码的RpcMessage对象
     * @param byteBuf  输出流
     * @throws Exception 编码过程中出现的异常
     */
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, RpcMessage rpcMessage, ByteBuf byteBuf) throws Exception {
        // 首先判断 是否为空
        if (rpcMessage == null || rpcMessage.getHeader() == null || rpcMessage.getBody() == null) {
            System.out.println("不能对空消息进行编码");
            throw new ProtocolException(MessageConstant.NOT_ENCODE_EMPTY_MSG);
        }

        // 获取序列化算法得到序列化器
        Serializer serializer = SerializerFactory.getSerializer(rpcMessage.getHeader().getSerializerAlgorithm());
        // 对消息体进行序列化
        byte[] bodyBytes = serializer.serialize(rpcMessage.getBody());
        // 获取序列化后的长度
        int bodyLength = bodyBytes.length;

        // 将消息信息写入输出流
        byteBuf.writeBytes(RpcProtocolConstant.MAGIC_NUMBER);
        byteBuf.writeByte(RpcProtocolConstant.VERSION);
        byteBuf.writeByte(rpcMessage.getHeader().getSerializerAlgorithm());
        byteBuf.writeByte(rpcMessage.getHeader().getMsgType());
        byteBuf.writeByte(rpcMessage.getHeader().getStatus());
        byteBuf.writeLong(rpcMessage.getHeader().getRequestID());
        byteBuf.writeInt(bodyLength);
        if (bodyLength > 0) {
             byteBuf.writeBytes(bodyBytes);
        }
        // Netty 会负责将这个 ByteBuf (out) 发送到网络。
        // 我们不需要手动调用 flush，除非有特殊需求。
        // MessageToByteEncoder 基类会在适当的时候处理flush。
    }
}
