package com.lxy.rpc.core.client;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Socket;

/**
 * 用于代理创建客户端代理对象，使用JDK动态代理
 */
public class RpcClientProxy implements InvocationHandler {
    // 记录日志
    private static final Logger logger = LoggerFactory.getLogger(RpcClientProxy.class);

    private final String host;
    private final int port;

    public RpcClientProxy(String host, int port) {
        this.host = host;
        this.port = port;
    }

    //  创建代理对象，在调用对应方法时，会自动调用下面的invoke方法
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> calzz) {
        return (T) Proxy.newProxyInstance(calzz.getClassLoader(), new Class<?>[]{calzz}, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        logger.info("客户端: 调用在 {}:{} 的方法 {}", host, port, method.getName());
        RpcRequest request = new RpcRequest(
                method.getDeclaringClass().getName(),
                method.getName(),
                method.getParameterTypes(),
                args);
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream())){
            System.out.println("invoke 连接获取成功");
            // 1. 发送请求
            output.writeObject(request);
            output.flush();
            logger.info("客户端: 发送请求, {}...等待服务端返回结果...",  request);

            // 2. 接收响应
            RpcResponse  response = (RpcResponse) input.readObject();
            if (response == null) {
                throw new RuntimeException("客户端: 返回响应是null");
            }
            if (response.hasException()) {
                throw response.getException();
            }
            return response.getResult();
        } catch (Exception e) {
            logger.error("客户端: 调用远程方法失败 " + method.getName(), e);
            throw new RuntimeException("客户端: 调用远程方法失败 " + e);
        }
    }
}
