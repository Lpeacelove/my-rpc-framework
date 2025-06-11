package com.lxy.rpc.core.common.exception;

public class RpcMethodNotFoundException extends RpcException {
    public RpcMethodNotFoundException(String message) {
        super(message);
    }
    public RpcMethodNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    public RpcMethodNotFoundException(Throwable cause) {
        super(cause);
    }
}
