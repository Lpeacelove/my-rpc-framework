package com.lxy.rpc.core.registry;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 服务实例变化监听器
 */
@FunctionalInterface //  函数式接口, 函数式接口只有一个抽象方法
public interface ServiceInstancesChangeListener {

    /**
     * 指定服务的实例列表发生变化时调用此方法。
     * @param serviceName 服务名称
     * @param newInstances 最新的服务实例地址列表
     */
    void onInstancesChange(String serviceName, List<InetSocketAddress> newInstances);
}
