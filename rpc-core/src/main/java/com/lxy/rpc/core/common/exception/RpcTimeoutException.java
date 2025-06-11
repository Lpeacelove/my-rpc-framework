package com.lxy.rpc.core.common.exception;

public class RpcTimeoutException extends RuntimeException {
    public RpcTimeoutException(String message) {
        super(message);
    }
    public RpcTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
    public RpcTimeoutException(Throwable cause) {
        super(cause);
    }
}
