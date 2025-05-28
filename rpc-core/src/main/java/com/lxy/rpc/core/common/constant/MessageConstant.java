package com.lxy.rpc.core.common.constant;

/**
 * 消息常量
 */
public class MessageConstant {

    // 序列化消息
    public static final String JDK_SERIALIZE_FAIL = "JDK 序列化失败";
    public static final String JDK_DESERIALIZE_FAIL = "JDK 反序列化失败";
    public static final String KRYO_SERIALIZE_FAIL = "KRYO 序列化失败";
    public static final String KRYO_DESERIALIZE_FAIL = "KRYO 反序列化失败";
    public static final String UNSUPPORTED_SERIALIZER_ALGORITHM = "不支持的序列化算法";
    public static final String FETCH_DEFAULT_SERIALIZER_FAIL = "默认序列化算法获取失败";
}
