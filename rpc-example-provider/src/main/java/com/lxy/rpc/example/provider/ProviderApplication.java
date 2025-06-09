package com.lxy.rpc.example.provider;

import com.lxy.rpc.api.HelloService;
import com.lxy.rpc.core.server.RpcServer;
import com.lxy.rpc.core.registry.LocalServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动类，用于启动RpcServer并注册服务
 */
public class ProviderApplication {

    public static void main(String[] args) {
        // 日志
        Logger logger = LoggerFactory.getLogger(ProviderApplication.class);
        logger.info("--- [Provider Application] Starting (Netty based) ---");

        // 1. 服务端创建本地注册表并给出zk地址
        LocalServiceRegistry  serviceRegistry = new LocalServiceRegistry(); // 创建本地注册表
        serviceRegistry.register(HelloService.class, new HelloServiceImpl());  // 注册服务到本地注册表
        String zkAddress = "127.0.0.1:2181";  //  定义zk地址

        // 2. 创建并配置RpcServer
        int port = 8088; // 定义服务端口
        RpcServer rpcServer = new RpcServer(port, serviceRegistry, zkAddress);  // 创建RpcServer

        // 3. 注册JVM shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("--- [ProviderApplication]: 正在通过hook关闭服务 ---");
            rpcServer.shutdown();
            logger.info("[ProviderApplication]: 通过hook成功关闭服务");
        }, "RpcServerShutdownHook"));

        try {
            System.out.println("[ProviderApplication]: 正在开始RPC Server 在端口 8088...");
            // 4. 启动Netty服务器 (这是一个阻塞操作，直到服务器关闭)
            rpcServer.start();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                System.err.println("[ProviderApplication]: RPC Server (Netty) startup was interrupted.");
                e.printStackTrace();
                Thread.currentThread().interrupt(); // 重新设置中断状态
            }
            System.err.println("[ProviderApplication]: An error occurred during RPC Server (Netty) startup or execution.");
            e.printStackTrace();
        } finally {
            System.out.println("--- Provider Application Exiting ---");
            // 如果 rpcServer.start() 正常返回（例如服务器被关闭），这里可以做一些清理
            // 但由于start()内部是阻塞的，通常main线程不会执行到这里，除非start()内部有退出机制或发生异常
        }
    }

}
