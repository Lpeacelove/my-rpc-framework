package com.lxy.rpc.core.client;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.common.constant.MessageConstant;
import com.lxy.rpc.core.common.exception.SerializationException;
import com.lxy.rpc.core.serialization.Serializer;
import com.lxy.rpc.core.serialization.SerializerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
    private final Serializer serializer;

    public RpcClientProxy(String host, int port) {
        this.host = host;
        this.port = port;
        this.serializer = SerializerFactory.getDefaultSerializer(); // 从工厂类获取默认的序列化器
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
//        try (Socket socket = new Socket(host, port);
//             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
//             ObjectInputStream input = new ObjectInputStream(socket.getInputStream())){
//            System.out.println("invoke 连接获取成功");
//            // 1. 发送请求
//            output.writeObject(request);
//            output.flush();
//            logger.info("客户端: 发送请求, {}...等待服务端返回结果...",  request);
//
//            // 2. 接收响应
//            RpcResponse  response = (RpcResponse) input.readObject();
//            if (response == null) {
//                throw new RuntimeException("客户端: 返回响应是null");
//            }
//            if (response.hasException()) {
//                throw response.getException();
//            }
//            return response.getResult();
//        } catch (Exception e) {
//            logger.error("客户端: 调用远程方法失败 " + method.getName(), e);
//            throw new RuntimeException("客户端: 调用远程方法失败 " + e);
//        }
        try (Socket socket = new Socket(host, port);
             OutputStream outputStream = socket.getOutputStream();
             InputStream inputStream = socket.getInputStream()) {
            System.out.println("RpcClientProxy invoke 方法获取连接成功");

            // 1. 在流中加入序列化算法
            outputStream.write(this.serializer.getSerializerAlgorithm());
            // 2. 使用序列化器将请求体序列化
            byte[] bytes = this.serializer.serialize(request);
            // 3. 将请求体写入流中（暂时不设计较复杂的协议，只考虑最简单的）
            outputStream.write(bytes);
            outputStream.flush();
            logger.info("客户端: 发送请求, {}...等待服务端返回结果...",  request);
            socket.shutdownOutput(); // 主动关闭输出流，告知服务端请求数据发送完毕 (对BIO读取很重要)

            // 4. 读取服务端返回的响应
            int responseSerializerAlgorithm = inputStream.read();
            if (responseSerializerAlgorithm == -1) {
                throw new SerializationException(MessageConstant.RESPONSE_SERIALIZER_ALGORITHM_NOT_FOUND);
            }
            Serializer responseSerializer = SerializerFactory.getSerializer((byte) responseSerializerAlgorithm);

            // 5. 读取响应流
            ByteArrayOutputStream responseBaos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                responseBaos.write(buffer, 0, bytesRead);
            }
            byte[] responseBytes = responseBaos.toByteArray();

            if (responseBytes.length == 0) {
                throw new SerializationException(MessageConstant.RESPONSE_BYTE_EMPTY);
            }

            // 6. 反序列化得到响应对象
            RpcResponse response = responseSerializer.deserialize(responseBytes, RpcResponse.class);
            if (response == null) {
                throw new SerializationException(MessageConstant.RESPONSE_DESERIALIZE_EMPTY);
            }

            if (response.hasException()) {
                throw response.getException();
            }
            return response.getResult();
        } catch (Exception e) {
            logger.error("客户端: 调用远程方法失败 " + method.getName(), e);
            throw new RuntimeException(MessageConstant.CLIENT_REMOTE_METHOD_FAIL + "method: " + method.getName() +
                    "host: " + host + "port: " + port, e);
        }

    }
}
