package com.lxy.rpc.core.serialization;

/**
 * 序列化接口，定义了对象与字节数组之间的转换规范
 * <p>
 *     RPC 框架使用此接口的实现来序列化和反序列化网络传输中的消息体。
 *     不同的实现可以支持不同的序列化算法，如：JDK、Kryo等。
 * </p>
 * <p>
 *     实现类应该通过 Java SPI 机制在 {@code META-INF/services/com.lxy.rpc.core.serialization.Serializer} 文件中注册
 * </p>
 *
 * @author lxy
 * @version 1.0
 */
public interface Serializer {

    /**
     * 将输入对象序列化为字节数组
     * @param object 需要序列化的对象
     * @return 返回序列化后的字节数组
     * @param <T> 待序列化对象的类型
     */
    <T> byte[] serialize(T object);

    /**
     * 反序列化字节数组为指定对象
     * @param bytes 需要反序列化的字节数组
     * @param clazz 需要反序列化的对象类型
     * @return 返回反序列化后的对象
     * @param <T> 待反序列化对象的类型
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
     * @return 返回序列化器的名称（全小写）
     */
    String getSerializerName();
}
