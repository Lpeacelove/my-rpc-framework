# MyRPCFramework: 一个从零开始构建的轻量级Java RPC框架

**MyRPCFramework** 是一个为了深入学习和理解RPC(远程过程调用)原理而从头开始实现的Java RPC框架。它涵盖了自定义通信协议、序列化/反序列化(支持Kyro序列化)、网络通信(支持BIO和高性能的Netty NIO)、服务注册与发现(集成ZooKeeper)、客户端负载均衡、心跳机制以及优雅停机等核心RPC功能。

本项目旨在通过实践来巩固分布式系统、网络编程、并发处理等后端核心技术，并探索构建一个生产级RPC框架所面临的挑战与解决方案。

## ✨核心特性

* **自定义通信协议：** 设计并实现了包含魔数、版本、序列化方式、消息类型、请求ID、数据长度的自定义应用层协议。
* **可插拔序列化：** 支持JDK原生序列化和高性能的Kryo序列化，通过SPI机制可扩展其他序列化方式(未来计划)。
* **高性能网络通信：** 底层网络通信支持从传统的Java BIO模型平滑升级到基于Netty的NIO模型，显著提升并发处理能力。
* **服务注册与发现：** 集成Apache ZooKeeper(使用Curator客户端)实现服务的动态注册、发现和地址列表的实时更新。
* **客户端负载均衡：** 支持多种负载均衡策略(如随机、轮询，未来可扩展加权、一致性哈希等)，并可通过配置选择。
* **心跳机制：** 客户端与服务端通过定时发送Ping/Pong消息维持长连接活跃，并及时检测和处理失效连接。
* **优雅停机：** 通过JVM Shutdown Hook实现客户端和服务端的平稳关闭，确保资源正确释放。
* **模块化设计：** 清晰的模块划分(api, core, example-provider, example-consumer)。

## 🛠️技术栈
* **核心语言：** Java(JDK 17+)
* **网络通信：** Netty(NIO), Java Socket(BIO - 早期版本)
* **序列化：** Kryo, JDk Native Serialization
* **服务注册与发现：** Apache ZooKeeper, Apache Curator
* **并发处理：** `java.util.concurrent` (如`CompletableFuture`, `AtomicLong`, `ExecutorService`)
* **日志：** SLF4J + Logback
* **构建工具：** Apache Maven

## 🏭项目架构(待实现)

### 核心流程简述：
1. **服务注册：** Provider启动时，通过`ServiceRegistry`组件(如`ZookeeperServiceRegistry`)将自身服务信息(接口名、IP、端口)注册到ZooKeeper的临时有序节点。
2. **服务发现：** Consumer在首次调用或需要时，通过`ServiceDiscovery`组件(如`ZookeeperServiceDiscovery`)从ZooKeeper查询指定服务名可用的Provider实例地址列表，并缓存。同时，会监听ZooKeeper中服务节点的变化，动态更新本地缓存。
3. **RPC调用(Consumer端)：**
   * 用户代码通过`RpcClientProxy`调用接口方法。
   * Proxy构建`RpcRequest`，包含接口名、方法、参数等。
   * `LoadBalancer`根据策略从服务发现获取的地址列表中选择一个目标Provider。
   * `RpcClient`(Netty客户端)负责与选定的Provider建立或复用连接。
   * `RpcRequest`封装成`RpcMesssage`(加上自定义通信协议头)，经过`RpcMessageEncoderNetty`(序列化数据体、编码协议头)转换为`ByteBuf`。
   * `ByteBuf`通过Netty Channel发送到Provider。
4. **RPC调用(Provider端)：**
   * Netty Channel接收到`ByteBuf`。
   * 经过`RpcFrameDecoder`(处理粘包/半包)和`RpcMessageDecoderNetty`(解码协议头、反序列化数据体)转化为`RpcMessage`(包含`RpcRequest)。
   * `RpcServerHandlerNetty`接收到`RpcMessage`，将其中的`RpcRequest`交给`RpcRequestHandler`。
   * `RpcRequestHandler`通过反射调用本地注册的`ServiceImplementation`的对应方法。
5. **响应返回：**
   * `ServiceImplementation`返回执行结果。
   * `RpcRequestHandler`将结果或异常封装到`RpcResponse`中。
   * `RpcResponse`封装成`RpcMessage`，经过`RpcMessageEndoerNetty`编码后通过Netty Channel发送回Consumer。
6. **响应处理(Consumer端):**
   * Consumer的Netty Channel接收响应`ByteBuf`。
   * 经过解码器链转换成`RpcMessage`(包含`RpcResponse`)。
   * `RpcClientHandler`处理响应`RpcMessage`，将其结果或异常传递给对应的`CompletableFuture`。
   * `RpcClientProxy`从`CompletableFuture`获取结果，返回给用户代码。
7. **心跳机制：** 客户端和服务器通过`IdleStateHandler`和自定义的`HeartbeatClientHandler`/`HeartbeatServerHandler`定期发送/响应Ping/Pong消息，以维持连接和检测失效。

## 🚀快速开始
### 先决条件
* JDK 17或更高版本
* Apache Maven 3.6+
* Apache ZooKeeper 3.7+(需要先启动一个ZooKeeper实例，默认连接地址`127.0.0.1:2181`)
### 构建项目
```bash
git clone(我的git项目)
cd my-rpc-framework
mvn clean package
```

## 🌰运行示例
1. 启动ZooKeeper服务器
2. 启动服务提供者(Provider):
   ```java
   # 打开一个新的终端，进入项目根目录，运行
   java -jar rpc-example-provider/target/rpc-example-provider-1.0-SNAPSHOT.jar
   # 或者在IDE中直接运行rpc-example-provider模块的ProviderApplication.java
   ```
   观察控制台输出，应有服务启动和注册到ZooKeeper的日志。
4. 启动服务消费(Consumer):
   ```
   # 打开另一个新的终端，进入项目根目录，运行
   java -jar rpc-example-consumer/target/rpc-example-consumer-1.0-SNAPSHOT.jar
   # 或者在IDE中直接运行rpc-example-consumer模块的ConsumerApplication.java
   ```
   观察控制台输出，应有从ZooKeeper发现服务、连接Provider并成功调用RPC方法的日志。

## 📁项目结构
```bash
my-rpc-framework/
├── rpc-api/                     # 定义服务接口和共享DTO
│   └── src/main/java/com/lxy/rpc/api/
├── rpc-core/                    # RPC框架核心实现
│   └── src/main/java/com/lxy/rpc/core/
│       ├── client/              # 客户端相关逻辑 (代理, Netty客户端, 心跳等)
│       ├── common/              # 通用工具类, 常量, 自定义异常
│       ├── loadbalance/         # 负载均衡策略实现
│       ├── protocol/            # 自定义协议定义 (消息结构, 编解码器)
│       ├── registry/            # 服务注册与发现接口及实现 (ZooKeeper)
│       ├── serialization/       # 序列化接口及实现
│       └── server/              # 服务端相关逻辑 (Netty服务端, 请求处理器, 心跳等)
├── rpc-example-provider/        # 服务提供者示例模块
│   └── src/main/java/com/lxy/rpc/example/provider/
├── rpc-example-consumer/        # 服务消费者示例模块
│   └── src/main/java/com/lxy/rpc/example/consumer/
├── docs/                        # (可选) 文档和图片资源
│   └── images/
├── pom.xml                      # Maven项目配置文件
└── README.md                    # 本文档
```

## ☑️测试(未来实现)

## 🗺️未来计划
* 实现更高级的负载均衡策略(如加权轮询、一致性哈希)
* 引入SPI机制，使核心组件(序列化、负载均衡、注册中心)可插拔
* 实现客户端连接池
* 服务端业务逻辑异步化
* 请求/响应数据压缩
* SSL/TLS加密传输
* 集成调用链追踪(如SkyWalking)
* 完善的单元测试和集成测试覆盖
* 更详细的文档和示例
