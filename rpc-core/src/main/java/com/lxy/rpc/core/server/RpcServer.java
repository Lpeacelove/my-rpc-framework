package com.lxy.rpc.core.server;

import com.lxy.rpc.core.server.registry.LocalServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 启动服务，监听端口，接收请求
 */
public class RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    private final int port;
    private final LocalServiceRegistry serviceRegistry;
    // todo 线程池相关内容
    private final ExecutorService threadPools; // 使用线程池处理请求

    public RpcServer(int port) {
        this.port = port;
        this.serviceRegistry = new LocalServiceRegistry();
        // 创建一个固定大小的线程池，可以根据需要调整
        this.threadPools = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    // 注册服务
    public <T> void register(Class<T> serviceInterface, T serviceImpl) {
        serviceRegistry.register(serviceInterface, serviceImpl);
    }

    // 启动服务
    public void start() {
        try(ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("服务端: RPC 服务器启动，监听端口 {}", port);
            while (true) {  // 持续监听
                try {
                    Socket clientSocket = serverSocket.accept();  // 阻塞等待客户端连接
                    logger.info("服务端: 接收到客户端连接 " + clientSocket.getInetAddress());
                    // 为每个客户端创建一个新的任务，并提交到线程池中处理
                    threadPools.execute(new RpcRequestHandler(clientSocket, serviceRegistry));
                } catch (IOException e) {
                    logger.error("服务端: 错误连接客户端 ", e);
                }
            }
        } catch (IOException e){
            logger.error("服务端: 不能监听到端口" + port, e);
            throw new RuntimeException("服务端: 启动失败");
        } finally {
            if (threadPools != null && !threadPools.isShutdown()) {
                threadPools.shutdown();
            }
        }
    }

}
