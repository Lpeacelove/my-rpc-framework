package com.lxy.rpc.api.dto;

import java.io.Serializable;
import java.util.Arrays;

/**
 * 请求对象，V0.1暂时可能可以直接传递参数，但先定义好结构，后续再扩展
 */
public class RpcRequest implements Serializable {
//    private String requestId; // 请求ID，用于后续异步处理
    private String interfaceName;
    private String methodName;
    private Class<?>[] parameterTypes;
    private Object[] parameters;

    public RpcRequest() {
    }

    public RpcRequest(String interfaceName, String methodName, Class<?>[] parameterTypes, Object[] parameters) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.parameters = parameters;
    }

//    public String getRequestId() {
//        return requestId;
//    }

//    public void setRequestId(String requestId) {
//        this.requestId = requestId;
//    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "RpcRequest{" +
                "interfaceName='" + interfaceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", parameterTypes=" + Arrays.toString(parameterTypes) +
                ", parameters=" + Arrays.toString(parameters) +
                '}';
    }
}
