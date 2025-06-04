package com.lxy.rpc.example.provider;

import com.lxy.rpc.api.HelloService;
import com.lxy.rpc.core.server.RpcServer;
import com.lxy.rpc.core.server.registry.LocalServiceRegistry;

/**
 * 启动类，用于启动RpcServer并注册服务
 */
public class ProviderApplication {

    public static void main(String[] args) {
        System.out.println("--- Provider Application Starting (Netty based) ---");

        // 1. 服务端创建本地注册表
        LocalServiceRegistry  serviceRegistry = new LocalServiceRegistry();
        serviceRegistry.register(HelloService.class, new HelloServiceImpl());

        // 2. 创建并配置RpcServer
        int port = 8088; // 定义服务端口
        RpcServer rpcServer = new RpcServer(port, serviceRegistry);

        try {
            System.out.println("Starting RPC Server (Netty) on port 8088...");
            // 3. 启动Netty服务器 (这是一个阻塞操作，直到服务器关闭)
            rpcServer.start();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                System.err.println("RPC Server (Netty) startup was interrupted.");
                e.printStackTrace();
                Thread.currentThread().interrupt(); // 重新设置中断状态
            }
            System.err.println("An error occurred during RPC Server (Netty) startup or execution.");
            e.printStackTrace();
        } finally {
            System.out.println("--- Provider Application Exiting ---");
            // 如果 rpcServer.start() 正常返回（例如服务器被关闭），这里可以做一些清理
            // 但由于start()内部是阻塞的，通常main线程不会执行到这里，除非start()内部有退出机制或发生异常
        }
    }

}
