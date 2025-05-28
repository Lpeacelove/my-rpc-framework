package com.lxy.rpc.core.server;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.common.constant.MessageConstant;
import com.lxy.rpc.core.common.exception.SerializationException;
import com.lxy.rpc.core.serialization.Serializer;
import com.lxy.rpc.core.serialization.SerializerFactory;
import com.lxy.rpc.core.server.registry.LocalServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.net.Socket;

/**
 * 处理单个客户端请求的线程逻辑，包含反射调用
 */
public class RpcRequestHandler implements Runnable{
    private static final Logger logger = LoggerFactory.getLogger(RpcRequestHandler.class);

    private final Socket socket;
    private final LocalServiceRegistry serviceRegistry;
    private final Serializer serializer;

    public RpcRequestHandler(Socket socket, LocalServiceRegistry serviceRegistry) {
        this.socket = socket;
        this.serviceRegistry = serviceRegistry;
        this.serializer = SerializerFactory.getDefaultSerializer();
        System.out.println("RpcRequestHandler 构造器执行了");
    }

    @Override
    public void run() {
        System.out.println("RpcRequestHandler run 方法开始执行");
        // 先获取连接
//        try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
//             ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream())) {
//            System.out.println("RpcRequestHandler run 方法获取连接成功");
//            // 1. 接收请求
//            RpcRequest request = (RpcRequest) inputStream.readObject();
//            logger.info("服务端：收到请求 {}", request);
//
//            // 2. 处理请求
//            RpcResponse response = handleRequest(request);
//
//            // 3. 返回结果
//            outputStream.writeObject(response);
//            outputStream.flush();
//            logger.info("服务端：返回结果 {}", response);
//
//        } catch (Exception e) {
//            logger.error("服务端：错误处理发生在 " + socket.getRemoteSocketAddress(), e);
//        } finally {
//            try {
//                if (socket != null && !socket.isClosed()) {
//                    socket.close();
//                }
//            } catch (Exception e) {
//                logger.error("服务端：关闭 socket 失败", e);
//            }
//        }
        try (InputStream inputStream = socket.getInputStream();
             OutputStream outputStream = socket.getOutputStream()) {
            System.out.println("RpcRequestHandler run 方法获取连接成功");
            // 1. 接收请求
            // 1.1 读取请求字节流中的序列化算法ID
            int serializerAlgorithm = inputStream.read();
            if (serializerAlgorithm == -1) {
                throw new SerializationException(MessageConstant.REQUEST_SERIALIZER_ALGORITHM_NOT_FOUND);
            }
            // 1.2 读取请求字节流
            ByteArrayOutputStream  bos = new ByteArrayOutputStream();
            byte[] bytes = new byte[4096];
            int readBytes = -1;
            while ((readBytes = inputStream.read(bytes)) != -1) {
                bos.write(bytes, 0, readBytes);
            }
            byte[] requestBytes = bos.toByteArray();
            if (requestBytes.length == 0) {
                throw new SerializationException(MessageConstant.REQUEST_BYTE_EMPTY);
            }

            // 1.3 反序列化得到请求对象
            Serializer requestSerializer = SerializerFactory.getSerializer((byte)serializerAlgorithm);
            RpcRequest request = requestSerializer.deserialize(requestBytes, RpcRequest.class);
            if (request == null) {
                throw new SerializationException(MessageConstant.REQUEST_DESERIALIZE_EMPTY);
            }
            logger.info("服务端：收到请求 {}", request);

            // 2. 处理请求
            RpcResponse response = handleRequest(request);

            // 3. 处理响应结果
            // 此处暂时使用相同的序列化算法，后续读取配置文件
            Serializer reponseSerializer = SerializerFactory.getDefaultSerializer();
            byte responseSerializerAlgorithm = reponseSerializer.getSerializerAlgorithm();
            outputStream.write(responseSerializerAlgorithm);
            byte[] responseBytes = reponseSerializer.serialize(response);
            outputStream.write(responseBytes);
            outputStream.flush();
            logger.info("服务端：返回结果 {}", response);
        } catch (Exception e) {
            logger.error("服务端：错误处理发生在 " + socket.getRemoteSocketAddress(), e);
            throw new RuntimeException(MessageConstant.SERVER_HANDLE_REQUEST_FAIL);
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
