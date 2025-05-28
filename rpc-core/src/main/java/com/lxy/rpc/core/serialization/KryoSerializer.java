package com.lxy.rpc.core.serialization;

/**
 * Kryo 序列化
 *
 * @author lxy
 */
public class KryoSerializer implements Serializer{



    @Override
    public <T> byte[] serialize(T object) {
        return new byte[0];
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        return null;
    }

    @Override
    public byte getSerializerAlgorithm() {
        return 0;
    }
}
