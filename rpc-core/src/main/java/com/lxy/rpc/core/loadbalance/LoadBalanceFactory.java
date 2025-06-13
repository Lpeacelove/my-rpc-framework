package com.lxy.rpc.core.loadbalance;

import com.lxy.rpc.core.config.RpcConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负载均衡工厂
 */
public class LoadBalanceFactory {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(LoadBalanceFactory.class);
    // 创建一个静态常量，用于存储所有的负载均衡策略，key为策略名称，value为策略对象
    private static final Map<String, LoadBalanceStrategy> NAME_TO_LOAD_BALANCE_STRATEGY_MAP = new ConcurrentHashMap<>();

    static {
        logger.info("初始化负载均衡策略...");
        ServiceLoader<LoadBalanceStrategy> loadBalanceStrategyServiceLoader = ServiceLoader.load(LoadBalanceStrategy.class);
        for (LoadBalanceStrategy loadBalanceStrategy : loadBalanceStrategyServiceLoader) {
            String name = loadBalanceStrategy.getName();
            if (name != null && !name.trim().isEmpty()) {
                if (NAME_TO_LOAD_BALANCE_STRATEGY_MAP.containsKey(name)) {
                    logger.warn("负载均衡策略名称[{}]已存在，请勿重复注册！", name);
                } else {
                    NAME_TO_LOAD_BALANCE_STRATEGY_MAP.put(name, loadBalanceStrategy);
                    logger.info("注册负载均衡策略[{}]成功！", name);
                }
            } else {
                logger.warn("负载均衡策略[{}]注册失败，请检查名称是否为空！", loadBalanceStrategy.getClass().getName());
            }
        }
        if (NAME_TO_LOAD_BALANCE_STRATEGY_MAP.isEmpty()) {
            logger.error("没有注册任何负载均衡策略！");
        }
        logger.info("负载均衡策略初始化完成！");
//        LoadBalanceStrategy randomLoadBalance = new RandomLoadBalance();
//        NAME_TO_LOAD_BALANCE_STRATEGY_MAP.put(randomLoadBalance.getName(), randomLoadBalance);
//
//        LoadBalanceStrategy roundRobinLoadBalance = new RoundRobinLoadBalance();
//        NAME_TO_LOAD_BALANCE_STRATEGY_MAP.put(roundRobinLoadBalance.getName(), roundRobinLoadBalance);
    }

    /**
     * 将负载均衡策略名称转换为标准格式
     * @param loadBalanceStrategyName 负载均衡策略名称
     * @return 标准格式的负载均衡策略名称
     */
    private static String normalize(String loadBalanceStrategyName) {
        return loadBalanceStrategyName.toLowerCase().replaceAll("-_", "");
    }

    /**
     * 根据负载均衡策略名称获取负载均衡策略对象
     * @param loadBalanceStrategyName 负载均衡策略名称
     * @return 负载均衡策略对象
     */
    public static LoadBalanceStrategy getLoadBalanceStrategy(String loadBalanceStrategyName) {
        return NAME_TO_LOAD_BALANCE_STRATEGY_MAP.get(normalize(loadBalanceStrategyName));
    }

    /**
     * 获取默认的负载均衡策略对象
     * @return 负载均衡策略对象
     */
    public static LoadBalanceStrategy getDefaultLoadBalanceStrategy() {
        return NAME_TO_LOAD_BALANCE_STRATEGY_MAP.get(normalize(RpcConfig.getClientLoadBalancerDefaultType()));
    }

}
