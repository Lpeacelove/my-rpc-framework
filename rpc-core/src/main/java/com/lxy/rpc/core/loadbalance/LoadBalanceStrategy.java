package com.lxy.rpc.core.loadbalance;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 负载均衡策略
 */
public interface LoadBalanceStrategy {

    /**
     * 根据负载均衡策略选择一个实例
     * @param serviceName 服务名
     * @param serviceInstances 服务实例
     * @return 选中的实例
     */
    InetSocketAddress select(String serviceName, List<InetSocketAddress> serviceInstances);
}
