package com.lxy.rpc.core.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.common.constant.MessageConstant;
import com.lxy.rpc.core.common.exception.SerializationException;

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
        // todo 需要在rpc-api 中添加自定义异常类
        kryo.register(Exception.class); // 注册RpcResponse 的异常
        return kryo;
    });

    /**
     * Kryo 序列化
     *
     * @param object
     * @param <T>
     * @return
     */
    @Override
    public <T> byte[] serialize(T object) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             Output output = new Output(bos)) { // Output包装了OutputStream
            kryoThreadLocal.get().writeObject(output, object);
//            output.flush(); output在close时会自动flush
            return output.toBytes(); // 获取底层流的字节
        } catch (Exception e) {
            e.printStackTrace();
            throw new SerializationException(MessageConstant.KRYO_SERIALIZE_FAIL);
        }
    }

    /**
     * Kryo 反序列化
     *
     * @param bytes
     * @param clazz
     * @param <T>
     * @return
     */
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             Input input = new Input(bis)) {
            return kryoThreadLocal.get().readObject(input, clazz); // 读取并转换为指定类型
        } catch (Exception e) {
            e.printStackTrace();
//            throw new SerializationException(MessageConstant.KRYO_DESERIALIZE_FAIL);
            throw new RuntimeException("Kryo 反序列化失败: " + e.getMessage(), e);
        }

    }

    @Override
    public byte getSerializerAlgorithm() {
        return KRYO_SERIALIZER_ALGORITHM;
    }
}
