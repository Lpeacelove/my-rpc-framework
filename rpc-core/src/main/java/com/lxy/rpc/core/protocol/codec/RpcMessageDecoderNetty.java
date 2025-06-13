package com.lxy.rpc.core.protocol.codec;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.common.constant.RpcErrorMessages;
import com.lxy.rpc.core.common.exception.RpcCodecException;
import com.lxy.rpc.core.protocol.MessageHeader;
import com.lxy.rpc.core.protocol.RpcMessage;
import com.lxy.rpc.core.protocol.RpcProtocolConstant;
import com.lxy.rpc.core.serialization.Serializer;
import com.lxy.rpc.core.serialization.SerializerFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * 对已经经过帧解码器拆分出完整帧信息的字节数组解码成RpcMessage对象
 */
public class RpcMessageDecoderNetty extends ByteToMessageDecoder {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(RpcMessageDecoderNetty.class);
    /**
     * Netty会调用这个方法来尝试从输入的 ByteBuf (in) 中解码出一个或多个 RpcMessage 对象。
     * @param ctx 获取channel的上下文
     * @param in 获取已经被帧解码后的字节数组
     * @param list 获取解码后的对象
     * @throws Exception 抛出异常
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> list) throws Exception {
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
            logger.error("[RpcMessageDecoderNetty] Invalid magic number from {}. Closing connection.",
                    ctx.channel().remoteAddress());
            // 关闭连接，防止进一步处理错误数据
            ctx.channel().close();
            throw new RpcCodecException("[RpcMessageDecoderNetty] " +
                    RpcErrorMessages.format(RpcErrorMessages.INVALID_MAGIC_NUMBER, ctx.channel().remoteAddress()));
        }

        // 4. 读取并校验版本号
        byte version = in.readByte();
        if (version != RpcProtocolConstant.VERSION) {
            logger.error("[RpcMessageDecoderNetty] Invalid version from {}. Closing connection.",
                    ctx.channel().remoteAddress());
            ctx.channel().close();
            throw new RpcCodecException("[RpcMessageDecoderNetty] " +
                    RpcErrorMessages.format(RpcErrorMessages.UNMATCHED_VERSION, ctx.channel().remoteAddress()));
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
            logger.error("[RpcMessageDecoderNetty] Invalid payload length {} from {}. Closing connection.",
                    bodyLength, ctx.channel().remoteAddress());
            ctx.channel().close();
            throw new RpcCodecException("[RpcMessageDecoderNetty] " +
                    RpcErrorMessages.format(RpcErrorMessages.ILLEGAL_DATA_LENGTH, ctx.channel().remoteAddress()));
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
            case RpcProtocolConstant.MSG_TYPE_HEARTBEAT_PING, RpcProtocolConstant.MSG_TYPE_HEARTBEAT_PONG:
                break;
            default:
                throw new RpcCodecException(RpcErrorMessages.format(RpcErrorMessages.UNEXPECTED_MESSAGE_TYPE, messageType));
        }

        MessageHeader header = new MessageHeader(magicNumber, version, serializerAlgorithm,
                messageType, status, requestID);
        header.setBodyLength(bodyLength);
        RpcMessage rpcMessage = new RpcMessage(header, data);

        list.add(rpcMessage);
        // ByteToMessageDecoder 会在 decode 方法返回后，检查 out 列表。
        // 如果列表不为空，它会逐个将对象传递下去。
        // 如果 in 中还有剩余字节，decode 方法会被再次调用，尝试解码更多消息。
    }
}
