package com.lxy.rpc.core.common.exception;

public class RpcServiceNotFoundException extends RpcException{
    public RpcServiceNotFoundException(String message) {
        super(message);
    }
    public RpcServiceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    public RpcServiceNotFoundException(Throwable cause) {
        super(cause);
    }
}
