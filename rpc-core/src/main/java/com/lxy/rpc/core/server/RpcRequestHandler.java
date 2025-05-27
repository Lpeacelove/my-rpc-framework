package com.lxy.rpc.core.server;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.server.registry.LocalServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.Socket;

/**
 * 处理单个客户端请求的线程逻辑，包含反射调用
 */
public class RpcRequestHandler implements Runnable{
    private static final Logger logger = LoggerFactory.getLogger(RpcRequestHandler.class);

    private final Socket socket;
    private final LocalServiceRegistry serviceRegistry;

    public RpcRequestHandler(Socket socket, LocalServiceRegistry serviceRegistry) {
        this.socket = socket;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public void run() {
        // 先获取连接
        try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {

            // 1. 接收请求
            RpcRequest request = (RpcRequest) inputStream.readObject();
            logger.info("服务端：收到请求 {}", request);

            // 2. 处理请求
            RpcResponse response = handleRequest(request);

            // 3. 返回结果
            outputStream.writeObject(response);
            outputStream.flush();
            logger.info("服务端：返回结果 {}", response);

        } catch (Exception e) {
            logger.error("服务端：错误处理发生在 " + socket.getRemoteSocketAddress(), e);
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception e) {
                logger.error("服务端：关闭 socket 失败", e);
            }
        }

    }

    private RpcResponse handleRequest(RpcRequest request) {
        RpcResponse response = new RpcResponse();
        try {
            Object service = serviceRegistry.getService(request.getInterfaceName());
            Method method = service.getClass().getMethod(request.getMethodName(), request.getParameterTypes());
            Object result = method.invoke(service, request.getParameters());
            response.setResult(result);
        } catch (Exception e) {
            logger.error("服务端：执行方法失败 " + request.getMethodName(), e);
            response.setException(e);
        }
        return response;
    }
}
