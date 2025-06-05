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
        System.out.println("--- Consumer Application Starting (Netty based) ---");

        // 1. 创建 RpcClientProxy 实例
        //    参数：服务端主机名/IP，服务端端口，请求超时时间（毫秒）
        RpcClientProxy clientProxy = new RpcClientProxy("127.0.0.1", 8088, 100000L); // 10秒超时

        // 2. 通过代理获取服务接口的实例
        HelloService helloService = clientProxy.getProxy(HelloService.class);
        System.out.println("Obtained proxy for HelloService.");

        // 3.像调用本地方法一样调用RPC方法
        try {
            System.out.println("Attempting to call helloService.sayHello(\"Netty RPC User\")...");
            String result = helloService.sayHello("Netty RPC User");
            System.out.println("RPC call result for sayHello: " + result);

//            System.out.println("\nAttempting to call helloService.sayHi(\"RPC Developer\")...");
//            String hiResult = helloService.sayHi("RPC Developer");
//            System.out.println("RPC call result for sayHi: " + hiResult);

            // 示例：测试一个不存在的方法（如果你的代理或服务端有相应处理，会抛出异常）
//             try {
//                 System.out.println("\nAttempting to call a non-existent method (for testing error handling)...");
//                 // 假设HelloService接口没有 "nonExistentMethod" 方法
//                 // 这会在代理创建时没问题，但在调用时，服务端反射找不到方法，会返回异常
//                 Object nonExistentResult = ((Object)helloService).getClass().getMethod("nonExistentMethod").invoke(helloService);
//                 System.out.println("Result of non-existent method (should not happen): " + nonExistentResult);
//             } catch (NoSuchMethodException nsme) {
//                 System.err.println("Caught NoSuchMethodException as expected locally (this is a test setup issue): " + nsme.getMessage());
//             } catch (RpcException e) {
//                  System.err.println("Caught RpcException for non-existent method: " + e.getMessage());
//                  // e.printStackTrace();
//             }


            // 示例：如果你的HelloServiceImpl.sayHello方法中对特定输入会抛出自定义业务异常
            // System.out.println("\nAttempting to call helloService.sayHello(\"throw_exception\")...");
            // try {
            //    String exceptionResult = helloService.sayHello("throw_exception");
            //    System.out.println("RPC call result for exception case: " + exceptionResult); // 不应执行到这里
            // } catch (YourBusinessException e) { // 假设你有一个YourBusinessException
            //    Syste1        111m.out.println("Successfully caught business exception from RPC call: " + e.getMessage());
            // }


        } catch (RpcException e) {
            // 捕获RPC框架层面定义的异常（例如连接失败、超时、序列化错误、服务端处理RPC请求时的通用错误等）
            System.err.println("An RpcException occurred during RPC call: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("  Underlying cause: " + e.getCause().getMessage());
            }
            e.printStackTrace();
        } catch (Throwable e) { // 捕获其他通过RPC传递过来的业务异常
            // 例如，如果服务端方法声明了 throws SomeBusinessException，
            // 并且该异常被正确序列化和反序列化，那么客户端这里可以捕获到它。
            System.err.println("An unexpected error (possibly business exception) occurred during RPC call: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 4. 在应用程序退出前，关闭 RpcClientProxy 以释放底层Netty资源
            System.out.println("Shutting down RpcClientProxy...");
            clientProxy.shutdown();
            System.out.println("--- Consumer Application Finished ---");
        }

    }

}
