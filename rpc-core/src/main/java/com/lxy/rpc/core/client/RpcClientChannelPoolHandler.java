package com.lxy.rpc.core.client;

import com.lxy.rpc.core.client.handler.HeartbeatClientHandler;
import com.lxy.rpc.core.config.RpcConfig;
import com.lxy.rpc.core.protocol.codec.RpcFrameDecoder;
import com.lxy.rpc.core.protocol.codec.RpcMessageDecoderNetty;
import com.lxy.rpc.core.protocol.codec.RpcMessageEncoderNetty;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * RpcClientChannelPoolHandler告诉FixedChannelPool创建什么样的Channel
 */
public class RpcClientChannelPoolHandler extends AbstractChannelPoolHandler {
    private static final Logger logger = LoggerFactory.getLogger(RpcClientChannelPoolHandler.class);

    /**
     * 当FixedChannelPool创建一个新的Channel时，会调用此方法
     * 在此处，为新创建的Channel添加一些必要的Handler
     * @param channel 新创建的Channel
     * @throws Exception 抛出异常
     */
    @Override
    public void channelCreated(Channel channel) throws Exception {
        logger.debug("channelCreated: {}", channel);
        ChannelPipeline pipeline = channel.pipeline();

        pipeline.addLast("loggerHandler", new LoggingHandler());
        pipeline.addLast("messageEncoder", new RpcMessageEncoderNetty());
        pipeline.addLast("frameDecoder", new RpcFrameDecoder());
        pipeline.addLast("messageDecoder", new RpcMessageDecoderNetty());

        long readIdleTimeoutSeconds = RpcConfig.getClientHeartbeatReadIdleTimeoutSeconds();
        long writeIdleTimeoutSeconds = RpcConfig.getClientHeartbeatWriteIdleTimeoutSeconds();
        pipeline.addLast("idleStateHandler", new IdleStateHandler(readIdleTimeoutSeconds, writeIdleTimeoutSeconds, 60, TimeUnit.SECONDS));
        int maxReaderIdleCounts = RpcConfig.getClientHeartbeatReadIdleCloseCount();
        pipeline.addLast("heartbeatHandler", new HeartbeatClientHandler(writeIdleTimeoutSeconds, maxReaderIdleCounts));

        pipeline.addLast("clientHandler", new RpcClientHandler());
    }
}
