package com.lxy.rpc.core.server;

import com.lxy.rpc.api.dto.RpcRequest;
import com.lxy.rpc.api.dto.RpcResponse;
import com.lxy.rpc.core.common.constant.MessageConstant;
import com.lxy.rpc.core.common.exception.SerializationException;
import com.lxy.rpc.core.protocol.*;
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
    private final MessageEncoder encoder;
    private final MessageDecoder decoder;

    public RpcRequestHandler(Socket socket, LocalServiceRegistry serviceRegistry) {
        this.socket = socket;
        this.serviceRegistry = serviceRegistry;
        this.serializer = SerializerFactory.getDefaultSerializer();
        this.encoder = new MessageEncoder();
        this.decoder = new MessageDecoder();
        System.out.println("RpcRequestHandler 构造器执行了");
    }

    @Override
    public void run() {
        System.out.println("RpcRequestHandler run 方法开始执行");
        try (InputStream inputStream = socket.getInputStream();
             OutputStream outputStream = socket.getOutputStream()) {
            System.out.println("RpcRequestHandler run 方法获取连接成功");

            RpcMessage<RpcRequest> requestMessage = decoder.decode(inputStream);
            if (requestMessage == null) {
                throw new SerializationException(MessageConstant.REQUEST_DESERIALIZE_EMPTY);
            }
            if (requestMessage.getHeader().getMsgType() == RpcProtocolConstant.MSG_TYPE_REQUEST) {
                RpcRequest  request = requestMessage.getBody();
                if (request == null) {
                    throw new SerializationException(MessageConstant.REQUEST_DESERIALIZE_EMPTY);
                }
                RpcResponse response = handleRequest(request);

                RpcMessage<RpcResponse> responseMessage = new RpcMessage<>(
                        new MessageHeader(
                                RpcProtocolConstant.MAGIC_NUMBER,
                                RpcProtocolConstant.VERSION,
                                this.serializer.getSerializerAlgorithm(),
                                RpcProtocolConstant.MSG_TYPE_RESPONSE,
                                RpcProtocolConstant.STATUS_SUCCESS,
                                Long.parseLong(request.getRequestId()),
                                0
                        ),
                        response
                );
                byte[] encode = encoder.encode(responseMessage);
                outputStream.write(encode);
                outputStream.flush();
                logger.info("服务端：返回结果 {}", response);
            } else {
                throw new SerializationException(MessageConstant.MSG_TYPE_WRONG);
            }
        } catch (Exception e) {
            logger.error("服务端：错误处理发生在 " + socket.getRemoteSocketAddress(), e);
            e.printStackTrace();
            throw new RuntimeException(MessageConstant.SERVER_HANDLE_REQUEST_FAIL);
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception e) {
                logger.error("服务端：关闭 socket 失败", e);
                e.printStackTrace();
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
