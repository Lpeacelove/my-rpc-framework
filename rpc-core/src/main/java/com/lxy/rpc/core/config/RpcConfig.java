package com.lxy.rpc.core.config;

import com.lxy.rpc.core.common.exception.RpcException;
import com.lxy.rpc.core.loadbalance.LoadBalanceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Rpc 配置加载类
 * 使用单例模式，确保配置只加载一次
 */
public final class RpcConfig { // 使用final类，确保该类不能被继承

    private static final Logger logger = LoggerFactory.getLogger(RpcConfig.class);
    private static final String DEFAULT_PROPERTIES_FILE = "rpc.properties";  // 默认配置文件名
    private static volatile Properties properties; // 使用volatile修饰，确保多线程访问时，属性值是最新的

    // 构造器私有化，确保无法通过new关键字创建实例
    private RpcConfig() {}

    // 静态代码块，类加载时执行一次
    static {
        loadProperties();
    }

    private static void loadProperties() {
        if (properties == null) { // 避免重复加载
            synchronized (RpcConfig.class) { // 同步块，确保多个线程不能同时加载配置
                if (properties == null) { // 再次检查，防止其他线程已经加载了配置。这是经典的双重检查锁模式
                    properties = new Properties();
                    try(InputStream input = RpcConfig.class.getClassLoader().getResourceAsStream(DEFAULT_PROPERTIES_FILE)) {
                        if (input != null) {
                            logger.info("正在加载配置文件：{}...", DEFAULT_PROPERTIES_FILE);
                            properties.load(input);
                        } else {
                            logger.warn("未找到配置文件：{}. 加载硬编码参数值", DEFAULT_PROPERTIES_FILE);
                            loadDefaultHardcodeValues();
                        }
                    } catch (Exception e) {
                        logger.error("加载配置文件失败：{}", DEFAULT_PROPERTIES_FILE, e);
                        throw new RpcException("加载配置文件失败：" + DEFAULT_PROPERTIES_FILE, e);
                    }
                }
                logger.info("已加载配置文件：{}", DEFAULT_PROPERTIES_FILE);
                // 允许通过系统属性覆盖配置文件的值
                Properties  systemProperties = System.getProperties();
                for (Object key : systemProperties.keySet()) {
                    String systemPropertyKey = key.toString();
                    if (systemPropertyKey.startsWith("rpc.")) {
                        String systemPropertyValue = systemProperties.getProperty(systemPropertyKey);
                        properties.setProperty(systemPropertyKey, systemPropertyValue);
                    }
                }
            }
        }
    }

    // 如果需要重新加载配置（不常用，除非有动态配置的需求）
    public static void reload() {
        properties = null;
        loadProperties();
        logger.info("已重新加载配置文件：{}", DEFAULT_PROPERTIES_FILE);
    }

    private static void loadDefaultHardcodeValues() {
        // 添加硬编码参数值
        properties.setProperty("rpc.server.port", "8088");
        properties.setProperty("rpc.client.default.timeout.ms", "5000");
        properties.setProperty("rpc.serialization.default.type", "kryo");
        properties.setProperty("rpc.registry.zookeeper.address", "127.0.0.1:2181");
        properties.setProperty("rpc.server.heartbeat.read.idle.timeout.seconds", "60");
        properties.setProperty("rpc.server.heartbeat.read.idle.close.count", "3");
        properties.setProperty("rpc.client.heartbeat.write.idle.timeout.seconds", "20");
        properties.setProperty("rpc.client.heartbeat.read.idle.timeout.seconds", "70");
        properties.setProperty("rpc.client.heartbeat.read.idle.close.count", "3");
        properties.setProperty("rpc.client.loadbalancer.default.type", "roundRobin");
    }

    // 提供获取各种配置项的方法

    /**
     * 获取字符串类型的配置项
     * @param key 配置键
     * @param defaultValue 如果键不存在或者值为空，则返回默认值
     * @return 配置值
     */
    public static String getString(String key, String defaultValue) {
        if (properties == null) loadProperties();
        return properties.getProperty(key, defaultValue);  // 如果键不存在或者值为空，则返回默认值
    }

    /**
     * 获取整数类型的配置项
     * @param key 配置键
     * @param defaultValue 如果键不存在或者值为空，则返回默认值
     * @return 配置值
     */
    public static int getInt(String key, int defaultValue) {
        if (properties == null) loadProperties();
        String valueStr = properties.getProperty(key);
        if (valueStr != null && !valueStr.trim().isEmpty()) {
            try {
                return Integer.parseInt(valueStr);
            } catch (NumberFormatException e) {
                logger.warn("配置项 {} 的值 {} 不是数字，将返回默认值 {}", key, valueStr, defaultValue);
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 获取布尔类型的配置项
     * @param key 配置键
     * @param defaultValue 如果键不存在或者值为空，则返回默认值
     * @return 配置值
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        if (properties == null) loadProperties();
        String valueStr = properties.getProperty(key);
        if (valueStr != null && !valueStr.trim().isEmpty()) {
            try {
                return Boolean.parseBoolean(valueStr);
            } catch (Exception e) {
                logger.warn("配置项 {} 的值 {} 不是布尔值，将返回默认值 {}", key, valueStr, defaultValue);
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 获取长整型类型的配置项
     * @param key 配置键
     * @param defaultValue 如果键不存在或者值为空，则返回默认值
     * @return 配置值
     */
    public static long getLong(String key, long defaultValue) {
        if (properties == null) loadProperties();
        String valueStr = properties.getProperty(key);
        if (valueStr != null && !valueStr.trim().isEmpty()) {
            try {
                return Long.parseLong(valueStr);
            } catch (NumberFormatException e) {
                logger.warn("配置项 {} 的值 {} 不是数字，将返回默认值 {}", key, valueStr, defaultValue);
                return defaultValue;
            }
        }
        return defaultValue;
    }

    // 为我们定义的具体配置项提供便捷的getter方法
    // 避免在业务代码中硬编码配置键字符串，并提供类型安全和默认值
    public static int getServerPort() {
        return getInt("rpc.server.port", 8088);
    }
    public static int getClientDefaultTimeoutMs() {
        return getInt("rpc.client.default.timeout.ms", 5000);
    }
    public static String getSerializationDefaultType() {
        return getString("rpc.serialization.default.type", "kryo");
    }
    public static String getRegistryZookeeperAddress() {
        return getString("rpc.registry.zookeeper.address", "127.0.0.1:2181");
    }
    public static long getServerHeartbeatReadIdleTimeoutSeconds() {
        return getLong("rpc.server.heartbeat.read.idle.timeout.seconds", 60);
    }
    public static int getServerHeartbeatReadIdleCloseCount() {
        return getInt("rpc.server.heartbeat.read.idle.close.count", 3);
    }
    public static long getClientHeartbeatWriteIdleTimeoutSeconds() {
        return getLong("rpc.client.heartbeat.write.idle.timeout.seconds", 20);
    }
    public static long getClientHeartbeatReadIdleTimeoutSeconds() {
        return getLong("rpc.client.heartbeat.read.idle.timeout.seconds", 70);
    }
    public static int getClientHeartbeatReadIdleCloseCount() {
        return getInt("rpc.client.heartbeat.read.idle.close.count", 3);
    }
    public static String getClientLoadBalancerDefaultType() {
        return getString("rpc.client.loadbalancer.default.type", "roundRobin");
    }
}
