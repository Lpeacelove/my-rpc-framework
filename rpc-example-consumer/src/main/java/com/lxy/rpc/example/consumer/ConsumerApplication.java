package com.lxy.rpc.example.consumer;

import com.lxy.rpc.api.HelloService;
import com.lxy.rpc.core.client.RpcClientProxy;
import com.lxy.rpc.core.common.exception.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动类，获取服务代理并调用
 */
public class ConsumerApplication {
    private static final Logger  logger = LoggerFactory.getLogger(ConsumerApplication.class);
    public static void main(String[] args) {
        logger.info("--- [Provider Application] Starting (Netty based) ---");

        // 1. 创建 RpcClientProxy 实例
        //    参数：服务端主机名/IP，服务端端口，请求超时时间（毫秒）
        String zkAddress = "127.0.0.1:2181";  //  zk地址
        RpcClientProxy clientProxy = new RpcClientProxy(zkAddress, 100000L); // 创建RpcClientProxy

        // 2. 通过代理获取服务接口的实例
        HelloService helloService = clientProxy.getProxy(HelloService.class);
        logger.info("[ConsumerApplication]: 创建 HelloService 的客户端代理");

        // 3. 注册JVM shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("====[ConsumerApplication]: 正在通过hook关闭服务====");
            clientProxy.shutdown();
            logger.info("[ConsumerApplication]: 通过hook成功关闭服务");
        }, "RpcClientProxyShutdownHook"));

//        // 4.1 正常调用方法进行测试
//        try {
//            logger.info("[ConsumerApplication]: 正在调用 helloService.sayHello...");
//            String result = helloService.sayHello(" 你好 泵就 奥拉 阿尼亚赛哟...");
//            logger.info("[ConsumerApplication]: helloService.sayHello 调用结果为: {}", result);
//
//            logger.info("====[ConsumerApplication]: 测试心跳机制以及ctrl+c优雅停机====");
//            Thread.sleep(Long.MAX_VALUE);
//        } catch (RpcException e) {
//            // 捕获RPC框架层面定义的异常（例如连接失败、超时、序列化错误、服务端处理RPC请求时的通用错误等）
//            logger.error("[ConsumerApplication]: An RpcException occurred during RPC call: {}", e.getMessage());
//            if (e.getCause() != null) {
//                logger.error("  Underlying cause: {}", e.getCause().getMessage());
//            }
//        } catch (Throwable e) { // 捕获其他通过RPC传递过来的业务异常
//            // 例如，如果服务端方法声明了 throws SomeBusinessException，
//            // 并且该异常被正确序列化和反序列化，那么客户端这里可以捕获到它。
//            logger.error("[ConsumerApplication]: An unexpected error (possibly business exception) occurred during RPC call: {}", e.getMessage());
//        } finally {
//            logger.info("[ConsumerApplication]: 方法执行结束");
//            // clientProxy.shutdown(); // 已经设置了hook，会自动关闭，这里可以不用调用，但也可以再次调用，但shutdown方法要设计为幂等，意思是多次调用无害或有内部状态判断的
//        }

//        // 4.2 示例：测试一个不存在的方法（如果你的代理或服务端有相应处理，会抛出异常）
//        try {
//            logger.info("====[ConsumerApplication]: 测试调用一个不存在的方法(为了测试异常处理)====");
//             // 假设HelloService接口没有 "nonExistentMethod" 方法
//             // 这会在代理创建时没问题，但在调用时，服务端反射找不到方法，会返回异常
//             Object nonExistentResult = ((Object)helloService).getClass().getMethod("nonExistentMethod").invoke(helloService);
//             System.out.println("Result of non-existent method (should not happen): " + nonExistentResult);
//        } catch (RpcException e) {
//            // 捕获RPC框架层面定义的异常（例如连接失败、超时、序列化错误、服务端处理RPC请求时的通用错误等）
//            System.err.println("An RpcException occurred during RPC call: " + e.getMessage());
//            if (e.getCause() != null) {
//                System.err.println("Underlying cause: " + e.getCause().getMessage());
//            }
//            e.printStackTrace();
//        } catch (Throwable e) { // 捕获其他通过RPC传递过来的业务异常
//            // 例如，如果服务端方法声明了 throws SomeBusinessException，
//            // 并且该异常被正确序列化和反序列化，那么客户端这里可以捕获到它。
//            System.err.println("An unexpected error (possibly business exception) occurred during RPC call: " + e.getMessage());
//            e.printStackTrace();
//        } finally {
//            logger.info("[ConsumerApplication]: 方法执行结束");
//        }



        // 4.3 进行多次调用，观察负载均衡效果（如果启动多个Provider实例）
        for (int i = 0; i < 50; i++) {
            try {
                logger.info("[ConsumerApplication]: 尝试调用 RPC #{}",  i + 1);
                String name = "吉米-" + i;
                String result = helloService.sayHello(name);
                logger.info("[ConsumerApplication]: RPC 调用 #{} 结果 for sayHello('{}'): {}", i + 1, name, result);
                Thread.sleep(500); // 短暂等待，方便观察日志
            } catch (RpcException e) {
                logger.error("[ConsumerApplication]: An RpcException occurred during RPC call #{}:{}",  i + 1, ": " + e.getMessage());
                if (e.getCause() != null) {
                    logger.error("[ConsumerApplication]: Underlying cause: {}", e.getCause().getMessage());
                }
            } catch (Throwable e) {
                logger.error("[ConsumerApplication]: An unexpected error occurred during RPC call #{}: {}", i + 1, e.getMessage());
            }
        }
        logger.info("[ConsumerApplication]: 方法执行结束");
    }

}
