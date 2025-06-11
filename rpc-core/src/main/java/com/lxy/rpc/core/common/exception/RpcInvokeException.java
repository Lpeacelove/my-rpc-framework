package com.lxy.rpc.core.common.exception;

public class RpcInvokeException extends RpcException{
    public RpcInvokeException(String message) {
        super(message);
    }
    public RpcInvokeException(String message, Throwable cause) {
        super(message, cause);
    }
    public RpcInvokeException(Throwable cause) {
        super(cause);
    }
}
