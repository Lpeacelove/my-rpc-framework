package com.lxy.rpc.example.provider;

import com.lxy.rpc.api.HelloService;
import com.lxy.rpc.core.server.RpcServer;

/**
 * 启动类，用于启动RpcServer并注册服务
 */
public class ProviderApplication {

    public static void main(String[] args) {
        int port = 8088; // 定义服务端口
        RpcServer rpcServer = new RpcServer(port);
        rpcServer.register(HelloService.class, new HelloServiceImpl());
        rpcServer.start();
    }

}
