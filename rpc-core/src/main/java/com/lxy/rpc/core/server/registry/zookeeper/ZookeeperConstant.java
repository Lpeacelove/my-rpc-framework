package com.lxy.rpc.core.server.registry.zookeeper;

/**
 * zookeeper常量
 */
public class ZookeeperConstant {

    // zookeeper根路径，关于RPC的所有服务，都存放在ZK_RPC_ROOT_PATH下
    public static final String ZK_RPC_ROOT_PATH = "/my-rpc";
    // zookeeper会话超时时间为5s
    public static final int ZK_SESSION_TIMEOUT = 5000;
    // zookeeper连接超时为5s
    public static final int ZK_CONNECTION_TIMEOUT = 5000;

    // curator重试策略，重试3次，每次间隔1s
    public static final int CURATOR_RETRY_BASE_SLEEP_MS = 1000;
    public static final int CURATOR_RETRY_MAX_RETRIES = 3;
}
