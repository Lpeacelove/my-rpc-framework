package com.lxy.rpc.core.protocol;

/**
 * 自定义协议相关的常量
 */
public class RpcProtocolConstant {

    // 魔数，4字节，用于快速识别协议
    public static final byte[] MAGIC_NUMBER = {(byte)'l', (byte)'a', (byte)'l', (byte)'a'}; // 魔数
    public static final int MAGIC_NUMBER_LENGTH = 4;

    // 协议主版号，1字节
    public static final byte VERSION = 0x01; // 版本号

    // 协议头部固定长度，20字节
    public static final byte HEADER_TOTAL_LENGTH = 20;

    // 消息类型定义，1字节
    public static final byte MSG_TYPE_REQUEST = 0x01;
    public static final byte MSG_TYPE_RESPONSE = 0x02;
    public static final byte MSG_TYPE_HEARTBEAT_PING = 0x03;
    public static final byte MSG_TYPE_HEARTBEAT_PONG = 0x04;

    // 响应状态定义，1字节
    public static final byte STATUS_SUCCESS = 0x01;
    public static final byte STATUS_FAIL = 0x02;

    // 消息头长度
    public static final int MESSAGE_HEADER_LENGTH = 20;

    // Netty 相关常量
    public static final int MAX_FRAME_LENGTH = 1024 * 1024;
    public static final int LENGTH_FIELD_OFFSET = 16;
    public static final int LENGTH_FIELD_LENGTH = 4;
    public static final int LENGTH_ADJUSTMENT = 0;
    public static final int INITIAL_BYTES_TO_STRIP = 0;

    // 私有化构造器，避免外部创建对象
    private RpcProtocolConstant(){}


}
