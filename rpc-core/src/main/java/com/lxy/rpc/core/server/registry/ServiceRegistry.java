package com.lxy.rpc.core.server.registry;

import java.net.InetSocketAddress;

/**
 * 服务注册接口
 */
public interface ServiceRegistry {

    /**
     * 注册服务
     * @param serviceName 服务名称
     * @param inetSocketAddress 服务地址
     */
    void registerService(String serviceName, InetSocketAddress inetSocketAddress);

    /**
     * 注销服务
     * @param serviceName 服务名称
     * @param inetSocketAddress 服务地址
     */
    void unregisterService(String serviceName, InetSocketAddress inetSocketAddress);

    /**
     * 关闭与注册中心的连接
     */
    void close();
}
