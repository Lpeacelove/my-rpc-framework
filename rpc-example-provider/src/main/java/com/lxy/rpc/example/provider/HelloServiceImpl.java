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
        String result = "Hello, " + name + " from RPC Provider!";
        logger.info("服务端: 执行sayHello for '{}', returning '{}'", name, result);
        return result;
    }
}
