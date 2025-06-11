package com.lxy.rpc.core.common.exception;

public class RpcNetworkException extends RpcException{
    public RpcNetworkException(String message) {
        super(message);
    }
    public RpcNetworkException(String message, Throwable cause) {
        super(message, cause);
    }
    public RpcNetworkException(Throwable cause) {
        super(cause);
    }
}
