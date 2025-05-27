package com.lxy.rpc.example.consumer;

import com.lxy.rpc.api.HelloService;
import com.lxy.rpc.core.client.RpcClientProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动类，获取服务代理并调用
 */
public class ConsumerApplication {
    private static final Logger  logger = LoggerFactory.getLogger(ConsumerApplication.class);
    public static void main(String[] args) {
        String  host = "127.0.0.1";
        int port = 8088;
        RpcClientProxy proxy = new RpcClientProxy(host, port);
        HelloService service = proxy.getProxy(HelloService.class);

        try {
            String result = service.sayHello("LXY");
            logger.info("服务端返回：{}", result);
            System.out.println(result);

//            // 测试一下错误结果
//            String result = service.sayHello(null);
//            logger.info("服务端返回：{}", result);
        } catch (Exception e) {
            logger.error("调用服务失败：{}", e.getMessage());
        }

    }

}
