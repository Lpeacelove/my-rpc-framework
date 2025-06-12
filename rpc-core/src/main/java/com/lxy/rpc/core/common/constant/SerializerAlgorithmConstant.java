package com.lxy.rpc.core.common.constant;

/**
 * 序列化算法字节标识常量
 */
public class SerializerAlgorithmConstant {

    public static final byte JDK_SERIALIZER_ALGORITHM = 0x01;
    public static final String JDK_SERIALIZER_ALGORITHM_NAME = "jdk";
    public static final byte KRYO_SERIALIZER_ALGORITHM = 0x02;
    public static final String KRYO_SERIALIZER_ALGORITHM_NAME = "kryo";

}
