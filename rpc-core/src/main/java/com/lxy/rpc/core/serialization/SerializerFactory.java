package com.lxy.rpc.core.serialization;

import com.lxy.rpc.core.common.constant.RpcErrorMessages;
import com.lxy.rpc.core.common.constant.SerializerAlgorithmConstant;
import com.lxy.rpc.core.common.exception.RpcSerializationException;
import com.lxy.rpc.core.config.RpcConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 创建一个工厂类，用于创建序列化器
 */
public class SerializerFactory {
    // 创建一个静态常量，用于加载配置文件时，找到对应的序列化算法
    private static final Map<String, Serializer> NAME_TO_SERIALIZER_MAP = new ConcurrentHashMap<>();
    // 创建一个静态常量，用于存储所有的序列化器, key为序列化算法的编号，value为对应的序列化器, ConcurrentHashMap 是线程安全的
    private static final Map<Byte, Serializer> ALGORITHM_TO_SERIALIZER_MAP = new ConcurrentHashMap<>();

    static {
        // 创建两个序列化器，并添加到MAP中
        Serializer jdkSerializer = new JdkSerializer();
        NAME_TO_SERIALIZER_MAP.put(SerializerAlgorithmConstant.JDK_SERIALIZER_ALGORITHM_NAME, jdkSerializer);
        ALGORITHM_TO_SERIALIZER_MAP.put(SerializerAlgorithmConstant.JDK_SERIALIZER_ALGORITHM, jdkSerializer);

        Serializer kryoSerializer = new KryoSerializer();
        NAME_TO_SERIALIZER_MAP.put(SerializerAlgorithmConstant.KRYO_SERIALIZER_ALGORITHM_NAME, kryoSerializer);
        ALGORITHM_TO_SERIALIZER_MAP.put(SerializerAlgorithmConstant.KRYO_SERIALIZER_ALGORITHM, kryoSerializer);
        // 后续其他序列化器也可在此进行注册
    }

    /**
     * 获取默认的序列化器
     * @return Serializer
     */
    public static Serializer getDefaultSerializer() {
        Serializer serializer = NAME_TO_SERIALIZER_MAP.get(RpcConfig.getSerializationDefaultType());
        if (serializer == null) {
            // 这种情况理论上在静态初始化后不应发生，除非KryoSerializer注册失败
            throw new RpcSerializationException(RpcErrorMessages.format(RpcErrorMessages.FETCH_DEFAULT_SERIALIZER_FAILED, RpcConfig.getSerializationDefaultType()));
        }
        return serializer;
    }

    /**
     * 获取指定编号的序列化器
     * @param serializerAlgorithm 序列化算法的编号
     * @return Serializer
     */
    public static Serializer getSerializer(byte serializerAlgorithm) {
        Serializer serializer = ALGORITHM_TO_SERIALIZER_MAP.get(serializerAlgorithm);
        if (serializer == null) {
            throw new RpcSerializationException(RpcErrorMessages.format(RpcErrorMessages.UNSUPPORTED_SERIALIZER_ALGORITHM, serializerAlgorithm));
        }
        // 获取指定编号的序列化器
        return serializer;
    }

    public static Serializer getSerializer(String serializerName) {
        if (serializerName == null || serializerName.trim().isEmpty()) {
            serializerName = RpcConfig.getSerializationDefaultType();
        }
        Serializer serializer = NAME_TO_SERIALIZER_MAP.get(serializerName);
        if (serializer == null) {
            throw new RpcSerializationException(RpcErrorMessages.format(RpcErrorMessages.UNSUPPORTED_SERIALIZER_ALGORITHM, serializerName));
        }
        // 获取指定编号的序列化器
        return serializer;
    }
}
