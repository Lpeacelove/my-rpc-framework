package com.lxy.rpc.core.common.exception;

/**
 * Rpc运行异常的基础类
 */
public class RpcException extends RuntimeException{
    public RpcException(String message) {
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcException(Throwable cause) {
        super(cause);
    }
}
