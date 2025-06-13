package com.lxy.rpc.core.serialization;

import com.lxy.rpc.core.common.constant.RpcErrorMessages;
import com.lxy.rpc.core.common.exception.RpcSerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static com.lxy.rpc.core.common.constant.SerializerAlgorithmConstant.JDK_SERIALIZER_ALGORITHM;

/**
 * Java自带序列化实现，V0.1使用
 */
public class JdkSerializer implements Serializer{

    /**
     * Java自带序列化实现
     * @param object
     * @param <T>
     * @return
     */
    @Override
    public <T> byte[] serialize(T object) {
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(object);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RpcSerializationException(RpcErrorMessages.format(RpcErrorMessages.JDK_SERIALIZE_FAILED, e));
        }
    }

    /**
     * Java自带反序列化实现
     * @param bytes
     * @param clazz
     * @param <T>
     * @return
     */
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try(ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis)) {
            return clazz.cast(ois.readObject());
        } catch (Exception e) {
            throw new RpcSerializationException(RpcErrorMessages.format(RpcErrorMessages.JDK_DESERIALIZE_FAILED, e));
        }
    }

    /**
     * 获取序列化算法
     * @return
     */
    @Override
    public byte getSerializerAlgorithm() {
        return JDK_SERIALIZER_ALGORITHM;
    }

    @Override
    public String getSerializerName() {
        return "jdk";
    }
}
