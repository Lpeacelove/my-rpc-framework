package com.lxy.rpc.core.server;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.common.constant.MessageConstant;
import com.lxy.rpc.core.common.exception.RpcException;
import com.lxy.rpc.core.common.exception.SerializationException;
import com.lxy.rpc.core.protocol.*;
import com.lxy.rpc.core.serialization.Serializer;
import com.lxy.rpc.core.serialization.SerializerFactory;
import com.lxy.rpc.core.server.registry.LocalServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;

/**
 * 处理单个客户端请求的线程逻辑，包含反射调用
 */
public class RpcRequestHandler{
    private static final Logger logger = LoggerFactory.getLogger(RpcRequestHandler.class);

    private final LocalServiceRegistry serviceRegistry;

    public RpcRequestHandler(LocalServiceRegistry serviceRegistry) {
        if (serviceRegistry == null) {
            throw new IllegalArgumentException("服务注册表不能为空");
        }
        this.serviceRegistry = serviceRegistry;
    }

    public RpcResponse handle(RpcRequest request) {
        if (request == null) {
            logger.warn("接收到一个空请求");
            RpcResponse nullRequestResponse = new RpcResponse();
            nullRequestResponse.setResponseId(null);
            nullRequestResponse.setException(new Exception("服务端: 接收到一个空请求"));
            return nullRequestResponse;
        }

        logger.debug("Handling RpcRequest: [ReqID={}, Interface={}, Method={}]",
                request.getRequestId(), request.getInterfaceName(), request.getMethodName());

        RpcResponse response = new RpcResponse();
        response.setResponseId(request.getRequestId());

        try {
            // 1. 从注册表中获取服务实例
            Object service = serviceRegistry.getService(request.getInterfaceName());
            if(service == null) {
                String errMsg = "Service not found for interface: " + request.getInterfaceName();
                logger.warn(errMsg + " (Request ID: " + request.getRequestId() + ")");
                response.setException(new Exception(errMsg));
                return response;
            }

            // 2. 获取要调用的方法对象
            Method method = service.getClass().getMethod(request.getMethodName(), request.getParameterTypes());
            if (method == null) {
                String errMsg = "Method not found for interface: " + request.getInterfaceName() + ", method: " + request.getMethodName();
                logger.warn(errMsg + " (Request ID: " + request.getRequestId() + ")");
                response.setException(new NoSuchMethodException(errMsg));
                return response;
            }

            // 3. 通过反射调用方法
            Object result = method.invoke(service, request.getParameters());

            // 4. 将执行结果设置到RpcResponse中
            response.setResult(result);
            logger.debug("Successfully invoked method {} for request ID {}, result type: {}",
                    request.getMethodName(), request.getRequestId(), (result != null ? result.getClass().getName() : "null"));
        } catch (NoSuchMethodException e) {
            String errMsg = "Method not found: " + request.getMethodName() + " with specified parameters in service: " + request.getInterfaceName();
            logger.warn(errMsg + " (Request ID: " + request.getRequestId() + ")", e);
            response.setException(new RpcException(errMsg));
        } catch (IllegalAccessException e) {
            String errMsg = "Illegal access while invoking method: " + request.getMethodName();
            logger.warn(errMsg + " (Request ID: " + request.getRequestId() + ")", e);
            response.setException(new RpcException(errMsg));
        } catch (InvocationTargetException e) {
            // InvocationTargetException 包装了被调用方法内部抛出的实际异常
            // 我们应该将这个实际异常设置到RpcResponse中，以便客户端能够感知到业务异常
            Throwable targetException = e.getTargetException();
            logger.warn("Method " + request.getMethodName() + " threw an exception (Request ID: " + request.getRequestId() + ")", targetException);
            response.setException((Exception) targetException); // 设置原始业务异常
        } catch (Exception e) {
            // 捕获其他所有可能的通用异常
            String errMsg = "Unexpected error during method invocation: " + request.getMethodName();
            logger.error(errMsg + " (Request ID: " + request.getRequestId() + ")", e);
            response.setException(new RpcException(errMsg));
        }
        return response;
    }
}

