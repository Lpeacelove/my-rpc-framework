package com.lxy.rpc.core.registry;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 服务发现
 */
public interface ServiceDiscovery {

    /**
     * 根据服务名获取服务地址
     * @param serviceName 服务名
     * @return 服务地址列表
     */
    List<InetSocketAddress> discoverService(String serviceName);

    /**
     * 订阅服务实例变化
     * @param serviceName 服务名
     * @param listener 服务实例变化监听器
     */
    void subscribe(String serviceName, ServiceInstancesChangeListener listener);

    /**
     * 取消订阅服务实例变化
     * @param serviceName 服务名
     * @param listener 服务实例变化监听器
     */
    void unsubscribe(String serviceName, ServiceInstancesChangeListener listener);

    /**
     * 关闭服务发现
     */
    void close();
}
