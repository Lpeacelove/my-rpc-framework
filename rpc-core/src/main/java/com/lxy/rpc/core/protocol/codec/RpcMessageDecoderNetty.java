package com.lxy.rpc.core.protocol.codec;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.common.constant.MessageConstant;
import com.lxy.rpc.core.common.exception.ProtocolException;
import com.lxy.rpc.core.protocol.MessageHeader;
import com.lxy.rpc.core.protocol.RpcMessage;
import com.lxy.rpc.core.protocol.RpcProtocolConstant;
import com.lxy.rpc.core.serialization.Serializer;
import com.lxy.rpc.core.serialization.SerializerFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.Arrays;
import java.util.List;

/**
 * 对已经经过帧解码器拆分出完整帧信息的字节数组解码成RpcMessage对象
 */
public class RpcMessageDecoderNetty extends ByteToMessageDecoder {
    /**
     * Netty会调用这个方法来尝试从输入的 ByteBuf (in) 中解码出一个或多个 RpcMessage 对象。
     * @param ctx 获取channel的上下文
     * @param in 获取已经被帧解码后的字节数组
     * @param list 获取解码后的对象
     * @throws Exception 抛出异常
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> list) throws Exception {
        System.out.println("RpcMessageDecoderNetty: netty解码器被调用，服务来自 " + ctx.channel().remoteAddress());
        // 1. 检查输入的字节数组长度是否小于消息头长度
        if (in.readableBytes() < RpcProtocolConstant.MESSAGE_HEADER_LENGTH) {
            return; // 直接返回，等待更多消息接收
        }

        // 2. 标记当前的位置，方便在后续的操作中恢复
        in.markReaderIndex();

        // 3. 读取并校验魔数
        byte[] magicNumber = new byte[RpcProtocolConstant.MAGIC_NUMBER_LENGTH];
        in.readBytes(magicNumber);
        // 魔数不匹配，说明接收到的数据不是我们的RPC协议包，或者数据已损坏。
        if (!Arrays.equals(magicNumber, RpcProtocolConstant.MAGIC_NUMBER)) {
            // 回滚到标记的位置
            in.resetReaderIndex();
            System.err.println("RpcMessageDecoderNetty: Invalid magic number from " +
                    ctx.channel().remoteAddress() + ". Closing connection.");
            // 关闭连接，防止进一步处理错误数据
            ctx.channel().close();
            throw new ProtocolException(MessageConstant.INVALID_MAGIC);
        }

        // 4. 读取并校验版本号
        byte version = in.readByte();
        if (version != RpcProtocolConstant.VERSION) {
            System.err.println("RpcMessageDecoderNetty: Invalid version from " +
                    ctx.channel().remoteAddress() + ". Closing connection.");
            ctx.channel().close();
            throw new ProtocolException(MessageConstant.INVALID_MAGIC);
        }

        // 5. 读取序列化算法ID
        byte serializerAlgorithm = in.readByte();
        // 6. 读取消息类型
        byte messageType = in.readByte();
        byte status = in.readByte();
        // 7. 读取消息ID
        long requestID = in.readLong();
        // 8. 读取消息体长度
        int bodyLength = in.readInt();

        // 9. 对消息体长度进行校验
        if (bodyLength < 0){
            System.err.println("RpcMessageDecoderNetty: Invalid payload length "
                    + bodyLength + " from " + ctx.channel().remoteAddress() + ". Closing connection.");
            ctx.channel().close();
            throw new ProtocolException(MessageConstant.BODY_LENGTH_ZERO);
        }

        // 10. 检查 ByteBuf 中剩余的可读字节是否足够包含完整的数据体
        if (in.readableBytes() < bodyLength) {
            // 数据体不完整，回滚 readerIndex，等待更多数据
            in.resetReaderIndex();
            return;
        }

        // 11. 读取消息体
        byte[] bodyBytes = new byte[bodyLength];
        in.readBytes(bodyBytes);
        // 12. 反序列化
        Serializer serializer = SerializerFactory.getSerializer(serializerAlgorithm);
        Object data = null;
        switch(messageType) {
            case RpcProtocolConstant.MSG_TYPE_REQUEST:
                data = serializer.deserialize(bodyBytes, RpcRequest.class);
                break;
            case RpcProtocolConstant.MSG_TYPE_RESPONSE:
                data = serializer.deserialize(bodyBytes, RpcResponse.class);
                break;
            case RpcProtocolConstant.MSG_TYPE_HEARTBEAT_REQUEST:
                break;
            case RpcProtocolConstant.MSG_TYPE_HEARTBEAT_RESPONSE:
                break;
            default:
                throw new ProtocolException(MessageConstant.UNSUPPORTED_MSG_TYPE);
        }

        MessageHeader header = new MessageHeader(magicNumber, version, serializerAlgorithm,
                messageType, status, requestID);
        header.setBodyLength(bodyLength);
        RpcMessage rpcMessage = new RpcMessage(header, data);

        System.out.println("RpcMessageDecoderNetty: 解码并反序列化后的信息: " + rpcMessage);
        list.add(rpcMessage);
        // ByteToMessageDecoder 会在 decode 方法返回后，检查 out 列表。
        // 如果列表不为空，它会逐个将对象传递下去。
        // 如果 in 中还有剩余字节，decode 方法会被再次调用，尝试解码更多消息。
    }
}
