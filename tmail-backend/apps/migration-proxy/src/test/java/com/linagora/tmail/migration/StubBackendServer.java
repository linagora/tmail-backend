/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.migration;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;

/**
 * A scriptable stand-in for a backend IMAP/SMTP server: it sends a greeting on connect, replies to
 * each received line according to a fixed script (keyed by a prefix of the line) and records every
 * line it received, so tests can assert what the proxy relayed.
 */
public class StubBackendServer implements AutoCloseable {
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup(1);
    private final String greeting;
    private final Map<String, String> scriptedReplies = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> receivedLines = new CopyOnWriteArrayList<>();
    private volatile Channel serverChannel;
    private volatile Channel lastClientChannel;

    public StubBackendServer(String greeting) {
        this.greeting = greeting;
    }

    public StubBackendServer reply(String linePrefix, String response) {
        scriptedReplies.put(linePrefix, response);
        return this;
    }

    public int start() throws InterruptedException {
        serverChannel = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    lastClientChannel = channel;
                    channel.pipeline().addLast(new LineBasedFrameDecoder(65536, false, false));
                    channel.pipeline().addLast(new StubHandler());
                }
            })
            .bind(new InetSocketAddress("127.0.0.1", 0))
            .sync()
            .channel();
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public CopyOnWriteArrayList<String> receivedLines() {
        return receivedLines;
    }

    public void pushToClient(String payload) {
        lastClientChannel.writeAndFlush(Unpooled.copiedBuffer(payload, StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    private final class StubHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(Unpooled.copiedBuffer(greeting + "\r\n", StandardCharsets.UTF_8));
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buffer = (ByteBuf) msg;
            try {
                String line = buffer.toString(StandardCharsets.UTF_8).replace("\r", "").replace("\n", "");
                receivedLines.add(line);
                scriptedReplies.entrySet().stream()
                    .filter(entry -> line.startsWith(entry.getKey()))
                    .findFirst()
                    .ifPresent(entry -> ctx.writeAndFlush(
                        Unpooled.copiedBuffer(entry.getValue() + "\r\n", StandardCharsets.UTF_8)));
            } finally {
                buffer.release();
            }
        }
    }
}
