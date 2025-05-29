package com.lxy.rpc.core.protocol;

import com.lxy.rpc.core.common.constant.MessageConstant;
import com.lxy.rpc.core.common.exception.ProtocolException;
import com.lxy.rpc.core.serialization.Serializer;
import com.lxy.rpc.core.serialization.SerializerFactory;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * 消息解码器
 * @author lxy
 * @date 2021-01-04
 */
@SuppressWarnings("unchecked")
public class MessageDecoder {

    public static <T> RpcMessage<T> decode(InputStream inputStream) throws Exception{
        DataInputStream dis = (inputStream instanceof DataInputStream ?
                (DataInputStream)inputStream : new DataInputStream(inputStream));

        // 1. 读取并校验魔数
        byte[] magicNumber = new byte[RpcProtocolConstant.MAGIC_NUMBER_LENGTH];
        int bytesRead = dis.read(magicNumber);
        if (bytesRead < RpcProtocolConstant.MAGIC_NUMBER_LENGTH) {
            if (bytesRead == -1) {  // 表示流已经结束，没有后续内容
                return null;
            }
            throw new ProtocolException(MessageConstant.READ_MAGIC_FAIL);
        }
        if (!Arrays.equals(magicNumber, RpcProtocolConstant.MAGIC_NUMBER)) {
            throw new ProtocolException(MessageConstant.INVALID_MAGIC);
        }

        // 2. 读取其他字段
        byte version = dis.readByte();
        byte serializerAlgorithm = dis.readByte();
        byte msgType = dis.readByte();
        byte status = dis.readByte();
        long requestID = dis.readLong();
        int bodyLength = dis.readInt();

        // 3. 校验版本号
        if (version != RpcProtocolConstant.VERSION) {
            throw new ProtocolException(MessageConstant.UNSUPPORTED_VERSION);
        }

        // 4. 判断消息体长度
        if (bodyLength < 0) {
            throw new ProtocolException(MessageConstant.BODY_LENGTH_ZERO);
        }

        // 5. 读取消息体
        byte[] bodyBytes = new byte[bodyLength];
        bytesRead = dis.read(bodyBytes);
        if (bytesRead < bodyLength) {
            throw new ProtocolException(MessageConstant.BODY_LENGTH_NOT_EQUAL_TO_BODY_LENGTH);
        }
        // 6. 反序列化
        Serializer serializer = SerializerFactory.getSerializer(serializerAlgorithm);
        Object data = switch (msgType) {
            case RpcProtocolConstant.MSG_TYPE_REQUEST -> serializer.deserialize(bodyBytes, RpcRequest.class);
            case RpcProtocolConstant.MSG_TYPE_RESPONSE -> serializer.deserialize(bodyBytes, RpcResponse.class);
//            case RpcProtocolConstant.MSG_TYPE_HEARTBEAT_REQUEST:
//                break;
//            case RpcProtocolConstant.MSG_TYPE_HEARTBEAT_RESPONSE:
//                break;
            default -> throw new ProtocolException(MessageConstant.UNSUPPORTED_MSG_TYPE);
        };
        // 根据消息类型进行反序列化

        // 7. 封装消息头
        MessageHeader header = new MessageHeader(magicNumber, version, serializerAlgorithm,
                msgType, status, requestID, bodyLength);
        return new RpcMessage<T>(header, (T)data);

    }
}
