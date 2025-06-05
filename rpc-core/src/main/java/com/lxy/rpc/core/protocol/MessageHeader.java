package com.lxy.rpc.core.protocol;

import java.util.Arrays;

/**
 * 消息头
 *
 * @author lxy
 */
public class MessageHeader {
    // 魔数
    private byte[] magicNumber = RpcProtocolConstant.MAGIC_NUMBER;
    // 版本号
    private byte version = RpcProtocolConstant.VERSION;
    // 序列化方式
    private byte serializerAlgorithm;
    // 消息类型
    private byte msgType;
    // 状态
    private byte status;
    // 请求id
    private long requestID;
    // 消息体长度
    private int bodyLength;

    public MessageHeader() {
    }

    public MessageHeader(byte[] magicNumber, byte version, byte serializerAlgorithm, byte msgType, byte status, long requestID) {
        this.magicNumber = magicNumber;
        this.version = version;
        this.serializerAlgorithm = serializerAlgorithm;
        this.msgType = msgType;
        this.status = status;
        this.requestID = requestID;
    }

    public byte[] getMagicNumber() {
        return magicNumber;
    }

    public void setMagicNumber(byte[] magicNumber) {
        this.magicNumber = magicNumber;
    }

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public byte getSerializerAlgorithm() {
        return serializerAlgorithm;
    }

    public void setSerializerAlgorithm(byte serializerAlgorithm) {
        this.serializerAlgorithm = serializerAlgorithm;
    }

    public byte getMsgType() {
        return msgType;
    }

    public void setMsgType(byte msgType) {
        this.msgType = msgType;
    }

    public byte getStatus() {
        return status;
    }

    public void setStatus(byte status) {
        this.status = status;
    }

    public long getRequestID() {
        return requestID;
    }

    public void setRequestID(long requestID) {
        this.requestID = requestID;
    }

    public int getBodyLength() {
        return bodyLength;
    }

    public void setBodyLength(int bodyLength) {
        this.bodyLength = bodyLength;
    }



    @Override
    public String toString() {
        return "MessageHeader{" +
                "magicNumber=" + Arrays.toString(magicNumber) +
                ", version=" + version +
                ", serializerAlgorithm=" + serializerAlgorithm +
                ", msgType=" + msgType +
                ", status=" + status +
                ", requestID=" + requestID +
                ", totalLength=" + bodyLength +
                '}';
    }
}
