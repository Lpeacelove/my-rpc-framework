package com.lxy.rpc.core.protocol;

import com.lxy.rpc.core.serialization.Serializer;
import com.lxy.rpc.core.serialization.SerializerFactory;

import java.awt.color.ProfileDataException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;


///**
// * 编码器
// */
//public class MessageEncoder {
//
//    /**
//     * 将 RpcMessage 编码为字节数组 (适用于BIO)
//     * @param rpcMessage 包含头部和原始body对象(RpcRequest/RpcResponse)的RpcMessage
//     * @param <T> body的类型
//     * @return 编码后的字节数组
//     */
//    public static <T> byte[] encode(RpcMessage<T> rpcMessage) {
//        if (rpcMessage == null || rpcMessage.getHeader() == null || rpcMessage.getBody() == null) {
//            throw new ProfileDataException(MessageConstant.NOT_ENCODE_EMPTY_MSG);
//        }
//
//        // 获取消息头和消息体
//        MessageHeader header = rpcMessage.getHeader();
//        T body = rpcMessage.getBody();
//
//        // 获取序列化算法
//        byte serializerAlgorithm = header.getSerializerAlgorithm();
//        Serializer serializer = SerializerFactory.getSerializer(serializerAlgorithm);
//        byte[] bodyBytes = serializer.serialize(body);
//        // 设置消息体长度
//        header.setBodyLength(bodyBytes.length);
//
//        try(ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            DataOutputStream dos = new DataOutputStream(baos)) {
//
//            // 消息头
//            dos.write(header.getMagicNumber()); // 魔数
//            dos.write(header.getVersion()); // 版本号
//            dos.write(header.getSerializerAlgorithm());  // 序列化算法
//            dos.write(header.getMsgType()); //  消息类型
//            dos.write(header.getStatus());  // 状态
//            dos.writeLong(header.getRequestID()); // 请求ID
//            dos.writeInt(header.getBodyLength()); // 消息体长度
//
//            // 消息体
//            dos.write(bodyBytes);
//            dos.flush();
//            return baos.toByteArray();
//        } catch (Exception e) {
//            throw new ProtocolException(MessageConstant.ENCODE_MSG_FAIL + e.getMessage());
//        }
//
//
//    }
//}
