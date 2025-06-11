package com.lxy.rpc.core.common.exception;

public class RpcSerializationException extends RpcException{
    public RpcSerializationException(String message) {
        super(message);
    }
    public RpcSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
    public RpcSerializationException(Throwable cause) {
        super(cause);
    }
}
