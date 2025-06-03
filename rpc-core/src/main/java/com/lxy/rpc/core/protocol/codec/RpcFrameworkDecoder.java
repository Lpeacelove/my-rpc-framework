package com.lxy.rpc.core.protocol.codec;

import com.lxy.rpc.core.protocol.RpcProtocolConstant;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * 帧解码器，解决粘包/半包问题。利用对“数据长度”的控制，从字节流中分离出完整的帧消息
 */
public class RpcFrameworkDecoder extends LengthFieldBasedFrameDecoder {
    public RpcFrameworkDecoder(){
        super(
                RpcProtocolConstant.MAX_FRAME_LENGTH, // 最大帧长度.防止恶意攻击或错误导致内存溢出。
                RpcProtocolConstant.LENGTH_FIELD_OFFSET,  // "数据长度(Data Length)"字段相对于整个消息头起始位置的偏移量。
                // 在我们的ProtocolConstants中，它是魔数、版本、序列化算法、消息类型、请求ID之后。
                RpcProtocolConstant.LENGTH_FIELD_LENGTH,  // "数据长度(Data Length)"字段本身占用的字节数。我们用int表示，所以是4字节。
                RpcProtocolConstant.LENGTH_ADJUSTMENT,  // 长度调整值。如果"数据长度"字段的值仅仅是数据体的长度，那么这个值为0。
                // 有些协议可能长度字段的值是 (数据体长度 + 某些头部字段长度)，此时就需要调整。
                RpcProtocolConstant.INITIAL_BYTES_TO_STRIP  // 从解码后的完整帧中需要跳过（丢弃）的初始字节数。
                // 如果我们希望解码器输出包含完整头部和数据体的帧，则设为0。
                // 如果只想输出数据体，可以设为头部长度 RpcProtocolConstant.HEADER_LENGTH。
                // 但通常我们会在下一个解码器中处理头部，所以这里保留头部。
        );
    }
}
