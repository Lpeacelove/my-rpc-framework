package com.lxy.rpc.core.serialization;

import com.lxy.rpc.core.common.constant.SerializerAlgorithmConstant;
import com.lxy.rpc.core.common.exception.SerializationException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.lxy.rpc.core.common.constant.MessageConstant.FETCH_DEFAULT_SERIALIZER_FAIL;
import static com.lxy.rpc.core.common.constant.MessageConstant.UNSUPPORTED_SERIALIZER_ALGORITHM;

/**
 * 创建一个工厂类，用于创建序列化器
 */
public class SerializerFactory {
    // 创建一个静态常量，用于存储所有的序列化器, key为序列化算法的编号，value为对应的序列化器, ConcurrentHashMap 是线程安全的
    private static final Map<Byte, Serializer> Serializer_MAP = new ConcurrentHashMap<>();

    static {
        // 创建两个序列化器，并添加到MAP中
        Serializer_MAP.put(SerializerAlgorithmConstant.JDK_SERIALIZER_ALGORITHM, new JdkSerializer());

        Serializer_MAP.put(SerializerAlgorithmConstant.KRYO_SERIALIZER_ALGORITHM, new KryoSerializer());
        // 后续其他序列化器也可在此进行注册
    }

    // todo 获取默认序列化器，后续使用配置文件读取，此处暂时写死
    private static final Serializer DEFAULT_SERIALIZER =
            getSerializer(SerializerAlgorithmConstant.KRYO_SERIALIZER_ALGORITHM);

    /**
     * 获取指定编号的序列化器
     * @param serializerAlgorithm 序列化算法的编号
     * @return Serializer
     */
    public static Serializer getSerializer(byte serializerAlgorithm) {
        Serializer serializer = Serializer_MAP.get(serializerAlgorithm);
        if (serializer == null) {
            throw new SerializationException(UNSUPPORTED_SERIALIZER_ALGORITHM + serializerAlgorithm);
        }
        // 获取指定编号的序列化器
        return Serializer_MAP.get(serializerAlgorithm);
    }
    /**
     * 获取默认的序列化器
     * @return Serializer
     */
    public static Serializer getDefaultSerializer() {
        Serializer serializer = Serializer_MAP.get(DEFAULT_SERIALIZER.getSerializerAlgorithm());
        if (serializer == null) {
            // 这种情况理论上在静态初始化后不应发生，除非KryoSerializer注册失败
            throw new SerializationException(FETCH_DEFAULT_SERIALIZER_FAIL + DEFAULT_SERIALIZER.getSerializerAlgorithm());
        }
        return serializer;
    }
}
