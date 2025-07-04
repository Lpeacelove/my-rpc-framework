package com.lxy.rpc.core.client;

import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPool;

/**
 * 用于缓存Client使用了哪个channel，以及这个channel属于哪个连接池
 * 方便在释放时通过channel获得释放回的连接池
 */
public class ChannelHolder {
    private final Channel channel;
    private final ChannelPool channelPool;

    public ChannelHolder(Channel channel, ChannelPool channelPool) {
        this.channel = channel;
        this.channelPool = channelPool;
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelPool getChannelPool() {
        return channelPool;
    }
}
