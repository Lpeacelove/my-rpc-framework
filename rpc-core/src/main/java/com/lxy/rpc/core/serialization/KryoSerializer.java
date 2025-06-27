package com.lxy.rpc.core.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.common.constant.RpcErrorMessages;
import com.lxy.rpc.core.common.exception.RpcSerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static com.lxy.rpc.core.common.constant.SerializerAlgorithmConstant.KRYO_SERIALIZER_ALGORITHM;

/**
 * Kryo 序列化
 *
 * @author lxy
 */
public class KryoSerializer implements Serializer{

    /**
     * ThreadLocal 避免 Kryo 实例复用导致的线程安全问题
     * Kryo 实例非线程安全，每个线程使用独立的 Kryo 实例
     */
    private final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();

        // 注册类以提高性能和减小体积
        // 注册顺序在 setRegistrationRequired(false) 时很重要
        kryo.register(RpcRequest.class);
        kryo.register(RpcResponse.class);
        kryo.register(Class.class); //
        kryo.register(Class[].class); // 注册RpcRequest 的参数类型数组
        kryo.register(Object[].class); // 注册RpcRequest 的参数数组
        kryo.register(String.class); // 注册RpcRequest 的参数名称
        kryo.register(Exception.class); // 注册RpcResponse 的异常
        return kryo;
    });

    /**
     * Kryo 序列化
     *
     * @param object 要进行序列化的对象
     * @param <T> 泛型
     * @return byte[]
     */
    @Override
    public <T> byte[] serialize(T object) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             Output output = new Output(bos)) { // Output包装了OutputStream
            kryoThreadLocal.get().writeObject(output, object);
            return output.toBytes(); // 获取底层流的字节
        } catch (Exception e) {
            System.out.println("kryo序列化失败" + e);
            throw new RpcSerializationException(RpcErrorMessages.format(RpcErrorMessages.KRYO_SERIALIZE_FAILED, e));
        }
    }

    /**
     * Kryo 反序列化
     *
     * @param bytes 要进行反序列化的字节数组
     * @param clazz 目标对象类型
     * @param <T> 泛型
     * @return 目标对象
     */
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             Input input = new Input(bis)) {
            return kryoThreadLocal.get().readObject(input, clazz); // 读取并转换为指定类型
        } catch (Exception e) {
            throw new RpcSerializationException(RpcErrorMessages.format(RpcErrorMessages.KRYO_DESERIALIZE_FAILED, e));
        }

    }

    /**
     * 获取序列化算法
     * @return byte
     */
    @Override
    public byte getSerializerAlgorithm() {
        return KRYO_SERIALIZER_ALGORITHM;
    }

    /**
     * 获取序列化算法名称
     * @return String
     */
    @Override
    public String getSerializerName() {
        return "kryo";
    }
}
