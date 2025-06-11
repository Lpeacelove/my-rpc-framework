package com.lxy.rpc.core.common.constant;

/**
 * Rpc 错误信息常量类
 */
public class RpcErrorMessages {
    // 服务相关
    public static final String SERVICE_NOT_AN_INTERFACE = "Service is not an interface";
    public static final String RPC_SERVICE_NOT_FOUND = "Not found any instances of %s";
    public static final String SERVICE_START_FAILED = "Service start failed for %s";



    // 注册相关
    public static final String NULL_ZK_ADDRESS = "ZkAddress cannot be null";
    public static final String ZK_SERVICE_DISCOVERY_FAILED = "Zk service discovery to %s failed for %s";
    public static final String ZK_SERVICE_REGISTRY_FAILED = "Zk service registry to %s failed for %s";
    public static final String CURATOR_CACHE_START_FAILED = "CuratorCache to %s failed for %s";
    public static final String REGISTER_SERVICE_FAILED = "Register service %s failed for %s";
    public static final String SERVICE_NOT_EXIST = "Service %s is not exist";
    public static final String NULL_LOCAL_SERVICE_REGISTRY = "Local service registry is null";



    // 消息相关
    public static final String NULL_RPC_MESSAGE = "Rpc message is null, Id[%s]";
    public static final String UNEXPECTED_MESSAGE_TYPE = "Received unexpected message type: %s";
    public static final String UNMATCHED_RESPONSE_ID = "Received unmatched responseId[%s]/requestId[%s]";
    public static final String NULL_RPC_BODY = "Rpc body is null, Id[%s]";

    // 网络通信相关
    public static final String CONNECT_FAILED = "Rpc connect to [%s] failed";
    public static final String INVALID_MAGIC_NUMBER = "Received invalid magic number form %s";
    public static final String UNMATCHED_VERSION = "Received unmatched version from %s";

    // 编解码相关
    public static final String ENCODE_FAILED = "Encode failed for %s";
    public static final String ILLEGAL_DATA_LENGTH = "Received illegal data length from %s";

    // 序列化相关
    public static final String JDK_SERIALIZE_FAILED = "JDK serialize failed for %s";
    public static final String JDK_DESERIALIZE_FAILED = "JDK deserialize failed for %s";
    public static final String KRYO_SERIALIZE_FAILED = "Kyro serialize failed for %s";
    public static final String KRYO_DESERIALIZE_FAILED = "Kyro deserialize failed for %s";
    public static final String UNSUPPORTED_SERIALIZER_ALGORITHM = "Unsupported serializer algorithm %s";
    public static final String FETCH_DEFAULT_SERIALIZER_FAILED = "Fetch default serializer failed";


    public static String format(String template, Object... args) {
        try {
            return String.format(template, args);
        } catch (Exception e) {
            // 如果格式化失败，则返回原始模板和参数，避免因为格式化错误而导致异常
            StringBuilder sb = new StringBuilder(template);
            for (Object arg : args) {
                sb.append(" | Args: ").append(arg);
            }
            return sb.toString();
        }
    }

    // 私有构造,防止创建实例
    private RpcErrorMessages(){}


}
