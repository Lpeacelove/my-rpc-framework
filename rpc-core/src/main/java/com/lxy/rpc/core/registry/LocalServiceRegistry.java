package com.lxy.rpc.core.registry;

import com.lxy.rpc.core.common.constant.RpcErrorMessages;
import com.lxy.rpc.core.common.exception.RpcRegistryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地服务注册表
 * 先使用 Map<String, Object> 存储服务对象，再使用 Map<String, String> 存储服务名称和服务对象的名称
 * @author lxy
 */
public class LocalServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(LocalServiceRegistry.class);

    // 创建了一个线程安全的 HashMap（ConcurrentHashMap）
    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();

    /**
     * 注册服务
     * @param serviceInterface
     * @param serviceImpl
     * @param <T>
     */
    public <T> void register (Class<T> serviceInterface, T serviceImpl) {
        String serviceName = serviceInterface.getName();
        if (serviceMap.containsKey(serviceName)) {
            logger.warn("[LocalServiceRegistry] 服务 {} 已经注册过了,正在覆盖", serviceName);
        }
        serviceMap.put(serviceName, serviceImpl);
        logger.info("[LocalServiceRegistry] 服务 {} -> {} 注册成功", serviceName,  serviceImpl.getClass().getName());
    }

    /**
     * 获取服务
     * @param serviceName
     * @return
     */
    public Object getService(String serviceName) {
        Object serviceInstance = serviceMap.get(serviceName);
        if (serviceInstance == null) {
            logger.warn("[LocalServiceRegistry] 服务 {} 不存在", serviceName);
            throw new RpcRegistryException("[LocalServiceRegistry] " +
                    RpcErrorMessages.format(RpcErrorMessages.SERVICE_NOT_EXIST, serviceName));
        }
        return serviceInstance;
    }

    /**
     * 获取已注册的服务名称
     * @return 服务名称
     */
    public Set<String> getRegisteredServiceNames() {
        return serviceMap.keySet();
    }

    /**
     * 判断是否为空
     * @return 是否为空
     */
    public boolean isEmpty() {
        return serviceMap.isEmpty();
    }
}
