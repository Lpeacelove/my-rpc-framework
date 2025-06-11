package com.lxy.rpc.core.common.exception;

public class RpcCodecException extends RpcException{
    public RpcCodecException(String message) {
        super(message);
    }
    public RpcCodecException(String message, Throwable cause) {
        super(message, cause);
    }
    public RpcCodecException(Throwable cause) {
        super(cause);
    }
}
