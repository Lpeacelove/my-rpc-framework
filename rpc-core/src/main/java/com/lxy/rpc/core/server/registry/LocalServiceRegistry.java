package com.lxy.rpc.core.server.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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
            logger.warn("服务 {} 已经注册过了,正在覆盖", serviceName);
        }
        serviceMap.put(serviceName, serviceImpl);
        logger.info("服务 {} -> {} 注册成功", serviceName,  serviceImpl.getClass().getName());
    }

    /**
     * 获取服务
     * @param serviceName
     * @return
     */
    public Object getService(String serviceName) {
        Object serviceInstance = serviceMap.get(serviceName);
        if (serviceInstance == null) {
            logger.warn("服务 {} 不存在", serviceName);
            throw new RuntimeException("服务 " + serviceName + " 不存在");
        }
        return serviceInstance;
    }
}
