package com.lxy.rpc.core.serialization;

import com.lxy.rpc.core.common.constant.RpcErrorMessages;
import com.lxy.rpc.core.common.constant.SerializerAlgorithmConstant;
import com.lxy.rpc.core.common.exception.RpcSerializationException;
import com.lxy.rpc.core.config.RpcConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化工厂类，用于加载注册文件中的所有序列化器
 *
 * @author lxy
 * @version 1.0
 */
public class SerializerFactory {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(SerializerFactory.class);

    // 创建一个静态常量，用于加载配置文件时，找到对应的序列化算法
    private static final Map<String, Serializer> NAME_TO_SERIALIZER_MAP = new ConcurrentHashMap<>();
    // 创建一个静态常量，用于存储所有的序列化器, key为序列化算法的编号，value为对应的序列化器, ConcurrentHashMap 是线程安全的
    private static final Map<Byte, Serializer> ALGORITHM_TO_SERIALIZER_MAP = new ConcurrentHashMap<>();

    static {
        logger.info("使用 ServiceLoader 初始化序列化器...");
        // 创建一个ServiceLoader对象，用于加载所有的序列化器
        ServiceLoader<Serializer> serviceLoader = ServiceLoader.load(Serializer.class);

        // 遍历所有的 Serializer对象
        for (Serializer serializer : serviceLoader) {
            String name = serializer.getSerializerName();
            byte algorithmId = serializer.getSerializerAlgorithm();

            if (name != null && !name.trim().isEmpty()) {
                if (NAME_TO_SERIALIZER_MAP.containsKey(name.toLowerCase())) {
                    logger.warn("已存在名为{}的序列化器，请勿重复注册", name);
                } else {
                    NAME_TO_SERIALIZER_MAP.put(name.toLowerCase(), serializer);
                    logger.info("已注册名为{}的序列化器", name);
                }
            } else {
                logger.warn("未指定序列化器的名称，请检查配置文件");
            }

            if (ALGORITHM_TO_SERIALIZER_MAP.containsKey(algorithmId)) {
                logger.warn("已存在编号{}的序列化器，请勿重复注册", algorithmId);
            } else {
                ALGORITHM_TO_SERIALIZER_MAP.put(algorithmId, serializer);
                logger.info("已注册编号{}的序列化器", algorithmId);
                // 如果是通过name注册的，确保ID也映射到同一个实例
                if (name != null && NAME_TO_SERIALIZER_MAP.get(name.toLowerCase()) == serializer) {
                    logger.debug("已注册编号为{}的序列化器，并已添加名为{}的映射", algorithmId, name);
                } else if (name != null) {
                    logger.warn("序列化器已通过 {} 注册，但其编号 {} mapping 不一致", name, algorithmId);
                }
            }
        }

        if (NAME_TO_SERIALIZER_MAP.isEmpty()) {
            logger.error("未找到任何序列化器，rpc框架可能无法工作，请检查配置文件");
        } else {
            logger.info("SerializerFactory 初始化完毕，加载序列化器有 {}", NAME_TO_SERIALIZER_MAP.keySet());
        }
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

    /**
     * 获取指定名称的序列化器
     * @param serializerName 序列化器的名称
     * @return Serializer
     */
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
