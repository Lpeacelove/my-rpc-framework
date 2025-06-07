package com.lxy.rpc.core.server.registry.zookeeper;

import java.net.InetSocketAddress;

/**
 * 工具类
 */
public class RpcFrameworkUtils {
    /**
     * 格式化地址
     * @param inetSocketAddress 地址
     * @return 格式化后的地址
     */
    public static String formatAddress(InetSocketAddress inetSocketAddress) {
        return inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort();
    }

    /**
     * 解析地址
     * @param address 地址ip:port
     * @return 地址InetSocketAddress
     */
    public static InetSocketAddress parseAddress(String address) {
        String[] addressArr = address.split(":");
        return new InetSocketAddress(addressArr[0], Integer.parseInt(addressArr[1]));
    }
}
