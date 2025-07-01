package com.lxy.rpc.example.provider;

import com.lxy.rpc.api.HelloService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 实现rpc-api中的HelloService接口
 */
public class HelloServiceImpl implements HelloService {
    private static final Logger logger = LoggerFactory.getLogger(HelloServiceImpl.class);

    @Override
    public String sayHello(String name) {
//        String result = "Hello, " + name + " from RPC Provider!";
//        logger.info("服务端: 执行sayHello for '{}', returning '{}'", name, result);
//        return result;

        logger.info("服务端: 开始执行 sayHello for '{}' (模拟耗时2秒)...", name);
        try {
            Thread.sleep(2000); // 模拟一个2秒的数据库查询或复杂计算
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String result = "Hello, " + name + " from RPC Provider!";
        logger.info("服务端: 完成 sayHello for '{}', returning '{}'", name, result);
        return result;
    }
}
