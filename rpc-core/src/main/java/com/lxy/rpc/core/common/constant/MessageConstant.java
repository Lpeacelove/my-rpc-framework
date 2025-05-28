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
    public static final String RESPONSE_SERIALIZER_ALGORITHM_NOT_FOUND = "服务端提前断开连接或未发送响应序列化ID";
    public static final String REQUEST_SERIALIZER_ALGORITHM_NOT_FOUND = "客户端提前断开连接或未发送请求序列化ID";
    public static final String RESPONSE_BYTE_EMPTY = "接收到从服务端返回的空响应";
    public static final String REQUEST_BYTE_EMPTY = "接收到从客户端发送的空响应";
    public static final String RESPONSE_DESERIALIZE_EMPTY = "接收到从服务端返回的响应反序列化为空";
    public static final String REQUEST_DESERIALIZE_EMPTY = "接收到从服务端客户端发送反序列化为空";
    public static final String CLIENT_REMOTE_METHOD_FAIL = "客户端调用远程方法失败";
    public static final String SERVER_HANDLE_REQUEST_FAIL = "服务端处理请求失败";
}
