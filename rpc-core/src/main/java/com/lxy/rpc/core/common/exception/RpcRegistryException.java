package com.lxy.rpc.core.common.exception;

public class RpcRegistryException extends RpcException{
    public RpcRegistryException(String message) {
        super(message);
    }
    public RpcRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
    public RpcRegistryException(Throwable cause) {
        super(cause);
    }
}
