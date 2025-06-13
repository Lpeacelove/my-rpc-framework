package com.lxy.rpc.core.serialization;

/**
 * 序列化接口，为后续扩展做准备
 */
public interface Serializer {

    /**
     * 将输入对象序列化为字节数组
     * @param object 需要序列化的对象
     * @return 返回序列化后的字节数组
     * @param <T> 泛型
     */
    <T> byte[] serialize(T object);

    /**
     * 反序列化字节数组为指定对象
     * @param bytes 需要反序列化的字节数组
     * @param clazz 需要反序列化的对象类型
     * @return 返回反序列化后的对象
     * @param <T> 泛型
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);

    /**
     * 获取当前序列化算法的唯一字节标识
     * 该字节将预置在序列化数据之前，以便接收方选择正确的反序列化器
     * @return 返回字节标识
     */
    byte getSerializerAlgorithm();

    /**
     * 获取当前序列化器的名称
     * @return 返回序列化器的名称
     */
    String getSerializerName();
}
