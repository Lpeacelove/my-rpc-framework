package com.lxy.rpc.core.loadbalance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡
 */
public class RoundRobinLoadBalance implements LoadBalanceStrategy{
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    @Override
    public InetSocketAddress select(String serviceName, List<InetSocketAddress> serviceInstances) {
        if (serviceInstances == null || serviceInstances.isEmpty()) {
            return null;
        }
        if (serviceInstances.size() == 1) {
            return serviceInstances.get(0);
        }
        // 获取当前列表索引并递增，然后对列表长度取模，得到新的索引，返回对应的地址
        // 注意：这里的实现方式在并发下，如果serviceInstances列表动态变化，可能会有越界或不均匀的问题。
        // 一个更健壮的轮询需要考虑服务列表动态变化的情况，例如使用版本号或更复杂的索引管理。
        // 为了简单演示，我们先用这种基础的轮询。
        int index = currentIndex.getAndIncrement() % serviceInstances.size();
        // 防止负数 (如果getAndIncrement()在某个边界情况下返回非常大的数导致溢出成负数)
        if (index < 0) {
            index = (index +  serviceInstances.size()) % serviceInstances.size();
        }
        return serviceInstances.get(index);
    }
}
