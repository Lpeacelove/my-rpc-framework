package com.lxy.rpc.core.loadbalance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机负载均衡
 */
public class RandomLoadBalance implements LoadBalanceStrategy{
    @Override
    public InetSocketAddress select(String serviceName, List<InetSocketAddress> serviceInstances) {
        if (serviceInstances == null || serviceInstances.isEmpty()) {
            return null;
        }
        if (serviceInstances.size() == 1) {
            return serviceInstances.get(0);
        }

        // // 使用 ThreadLocalRandom 获取随机数，性能更好且线程安全
        int randomIndex = ThreadLocalRandom.current().nextInt(serviceInstances.size());
        return serviceInstances.get(randomIndex);
    }

    @Override
    public String getName() {
        return "random";
    }
}
