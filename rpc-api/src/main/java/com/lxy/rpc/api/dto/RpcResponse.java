package com.lxy.rpc.api.dto;

import java.io.Serializable;

/**
 * 响应对象，用于封装远程调用的响应结果，包括响应结果和异常信息，V0.1可能直接返回结果，但先定义好结构，后续再优化
 */
public class RpcResponse implements Serializable {
    private static final long serialVersionUID = 2L;

    private String responseId; // 响应ID，用于后续异步处理
    private Object result;
    private Exception exception;

    public RpcResponse() {
    }

    public RpcResponse(Object result) {
        this.result = result;
    }

    public RpcResponse(Exception exception) {
        this.exception = exception;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    /**
     * 判断是否异常
     * @return
     */
    public boolean hasException() {
        return exception != null;
    }

    @Override
    public String toString() {
        return "RpcResponse{" +
                "result=" + result +
                ", exception=" + exception +
                '}';
    }
}
