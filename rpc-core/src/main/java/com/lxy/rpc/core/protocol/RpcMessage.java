package com.lxy.rpc.core.protocol;

/**
 * 自定义消息（包括消息头和消息体（尚未序列化）
 */
public class RpcMessage<T> {
    private MessageHeader header;
    private T body;  // 尚未进行序列化前的数据

    public RpcMessage() {
    }

    public RpcMessage(MessageHeader header, T body) {
        this.header = header;
        this.body = body;
    }

    public MessageHeader getHeader() {
        return header;
    }

    public void setHeader(MessageHeader header) {
        this.header = header;
    }

    public T getBody() {
        return body;
    }

    public void setBody(T body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "RpcMessage{" +
                "header=" + header +
                ", body=" + (body != null? body.toString() : "null") +
                '}';
    }
}
