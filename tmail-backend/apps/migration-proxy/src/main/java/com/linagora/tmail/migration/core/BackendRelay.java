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

package com.linagora.tmail.migration.core;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import org.apache.james.protocols.api.ProxyInformation;
import org.apache.james.protocols.netty.HandlerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;

/**
 * Once the proxy has identified the user and picked a backend, this opens a client connection to the
 * backend, authenticates there on the user's behalf (the {@link BackendDialog} handshake) and then
 * pipes raw bytes both ways between the client and the backend channels.
 *
 * <p>The handover happens in two steps so that the protocol-specific success response (SMTP {@code 235},
 * IMAP tagged {@code OK}) can be emitted at the right time:
 * <ol>
 *     <li>{@link #connectAndAuthenticate} connects + authenticates to the backend and wires the
 *     backend &rarr; client direction. Client reads stay paused.</li>
 *     <li>{@link #takeOverClient} strips the proxy protocol stack and wires the client &rarr; backend
 *     direction, resuming client reads.</li>
 * </ol>
 * Both calls are blocking and meant to be invoked from a protocol handler thread.
 */
public class BackendRelay {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackendRelay.class);
    private static final int MAX_LINE_LENGTH = 65536;
    private static final String CRLF = "\r\n";

    private final MigrationProxyMetrics metrics;

    @jakarta.inject.Inject
    public BackendRelay(MigrationProxyMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Everything the relay needs to open and authenticate a single backend connection on the user's
     * behalf, bundled so {@link #connectAndAuthenticate} stays a two-argument handover.
     */
    public record RelayRequest(Backend backend, Supplier<BackendDialog> dialogFactory,
                               Optional<SslContext> sslContext, Duration timeout,
                               Optional<ProxyInformation> inboundProxyInfo) {
    }

    /**
     * Connects to the backend and replays the authentication. On success the backend &rarr; client
     * piping is installed and the returned channel must later be passed to {@link #takeOverClient}.
     * Client reads remain paused until then.
     */
    public Optional<Channel> connectAndAuthenticate(Channel clientChannel, RelayRequest request) {
        Backend backend = request.backend();
        Supplier<BackendDialog> dialogFactory = request.dialogFactory();
        Optional<SslContext> sslContext = request.sslContext();
        Duration timeout = request.timeout();
        Optional<ProxyInformation> inboundProxyInfo = request.inboundProxyInfo();

        if (backend.forwardProxyInfo() && inboundProxyInfo.isEmpty()) {
            throw new MissingProxyInformationException(backend);
        }
        Optional<String> proxyProtocolHeader = forwardedProxyHeader(backend, inboundProxyInfo);

        clientChannel.config().setAutoRead(false);

        CompletableFuture<Channel> handshakeResult = new CompletableFuture<>();

        Bootstrap bootstrap = new Bootstrap()
            .group(clientChannel.eventLoop())
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel backendChannel) {
                    proxyProtocolHeader.ifPresentOrElse(
                        header -> backendChannel.pipeline().addLast("proxyProtocol",
                            new ProxyProtocolInitHandler(header, backend, sslContext)),
                        () -> sslContext.ifPresent(ssl -> backendChannel.pipeline()
                            .addLast("ssl", newSslHandler(ssl, backendChannel, backend))));
                    backendChannel.pipeline().addLast("framer", new LineBasedFrameDecoder(MAX_LINE_LENGTH, false, false));
                    backendChannel.pipeline().addLast("handshake",
                        new HandshakeHandler(clientChannel, backend, dialogFactory.get(), handshakeResult));
                }
            });

        bootstrap.connect(backend.host().getHostName(), backend.host().getPort())
            .addListener(future -> {
                if (!future.isSuccess()) {
                    handshakeResult.completeExceptionally(future.cause());
                }
            });

        try {
            Channel backendChannel = handshakeResult.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            metrics.recordConnection(backend);
            return Optional.of(backendChannel);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            clientChannel.config().setAutoRead(true);
            return Optional.empty();
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("Failed establishing relay to backend {}", backend, e);
            clientChannel.config().setAutoRead(true);
            return Optional.empty();
        }
    }

    /**
     * Strips the proxy protocol stack and starts forwarding client bytes to the backend. To be called
     * once the protocol-specific authentication success response has been emitted to the client.
     */
    public void takeOverClient(Channel clientChannel, Channel backendChannel, Backend backend) {
        runOnEventLoop(clientChannel, () -> {
            stripProtocolStack(clientChannel.pipeline());
            // Once the server protocol handlers are gone the proxying is a bare byte pipe: the RelayHandler
            // forwards each inbound buffer to the backend raw. We deliberately do not re-frame the client
            // stream by lines - a LineBasedFrameDecoder capped at MAX_LINE_LENGTH would reject large IMAP
            // literals (e.g. a multi-megabyte APPEND) that legitimately carry no CRLF for many bytes.
            clientChannel.pipeline().addLast(new RelayHandler(backendChannel,
                bytes -> metrics.recordBytesToBackend(backend, bytes)));
            clientChannel.config().setAutoRead(true);
        });
    }

    private void runOnEventLoop(Channel channel, Runnable runnable) {
        if (channel.eventLoop().inEventLoop()) {
            runnable.run();
        } else {
            channel.eventLoop().submit(runnable).awaitUninterruptibly();
        }
    }

    /**
     * Reads the backend greeting/auth replies line by line and drives the {@link BackendDialog}. On
     * success it wires the backend &rarr; client direction and completes the handshake future.
     */
    private final class HandshakeHandler extends ChannelInboundHandlerAdapter {
        private final Channel clientChannel;
        private final Backend backend;
        private final BackendDialog dialog;
        private final CompletableFuture<Channel> result;

        private HandshakeHandler(Channel clientChannel, Backend backend,
                                 BackendDialog dialog, CompletableFuture<Channel> result) {
            this.clientChannel = clientChannel;
            this.backend = backend;
            this.dialog = dialog;
            this.result = result;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buffer = (ByteBuf) msg;
            try {
                String line = buffer.toString(CharsetUtil.UTF_8).replace("\r", "").replace("\n", "");
                BackendDialog.Action action = dialog.onLine(line);
                switch (action.decision()) {
                    case SEND -> ctx.writeAndFlush(Unpooled.copiedBuffer(action.command() + CRLF, CharsetUtil.UTF_8));
                    case SUCCESS -> activateBackendSide(ctx.channel());
                    case FAILURE -> fail(ctx, "backend rejected authentication: " + line);
                    case WAIT -> {
                        // keep reading further backend lines
                    }
                }
            } finally {
                buffer.release();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            fail(ctx, "error while authenticating against backend: " + cause.getMessage());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!result.isDone()) {
                result.completeExceptionally(new IllegalStateException("Backend closed during handshake"));
            }
        }

        private void activateBackendSide(Channel backendChannel) {
            backendChannel.pipeline().remove("framer");
            backendChannel.pipeline().remove("handshake");
            backendChannel.pipeline().addLast(new RelayHandler(clientChannel,
                bytes -> metrics.recordBytesToClient(backend, bytes)));
            result.complete(backendChannel);
        }

        private void fail(ChannelHandlerContext ctx, String reason) {
            LOGGER.info("Relay handshake to {} failed: {}", backend, reason);
            ctx.close();
            if (!result.isDone()) {
                result.completeExceptionally(new IllegalStateException(reason));
            }
        }
    }

    private static SslHandler newSslHandler(SslContext sslContext, Channel backendChannel, Backend backend) {
        return sslContext.newHandler(backendChannel.alloc(), backend.host().getHostName(), backend.host().getPort());
    }

    private static Optional<String> forwardedProxyHeader(Backend backend, Optional<ProxyInformation> inboundProxyInfo) {
        if (!backend.forwardProxyInfo()) {
            return Optional.empty();
        }
        return inboundProxyInfo.map(info -> proxyProtocolV1Header(info.getSource(), info.getDestination()));
    }

    /**
     * HAProxy PROXY protocol (v1) header carrying the original client address, so the backend sees the
     * real client rather than the proxy. Written in clear text as the very first bytes of the
     * connection, before any TLS handshake.
     */
    private static String proxyProtocolV1Header(InetSocketAddress source, InetSocketAddress destination) {
        if (isIncomplete(source) || isIncomplete(destination)) {
            return "PROXY UNKNOWN\r\n";
        }
        String family = source.getAddress() instanceof Inet6Address ? "TCP6" : "TCP4";
        return "PROXY " + family + " "
            + source.getAddress().getHostAddress() + " "
            + destination.getAddress().getHostAddress() + " "
            + source.getPort() + " "
            + destination.getPort() + "\r\n";
    }

    private static boolean isIncomplete(InetSocketAddress address) {
        return address == null || address.getAddress() == null;
    }

    /**
     * Sends the forwarded PROXY protocol header first, then (when the backend uses implicit TLS) installs
     * the {@link SslHandler} so the TLS handshake follows the clear-text header.
     */
    private static final class ProxyProtocolInitHandler extends ChannelInboundHandlerAdapter {
        private final String header;
        private final Backend backend;
        private final Optional<SslContext> sslContext;

        private ProxyProtocolInitHandler(String header, Backend backend, Optional<SslContext> sslContext) {
            this.header = header;
            this.backend = backend;
            this.sslContext = sslContext;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(Unpooled.copiedBuffer(header, CharsetUtil.US_ASCII));
            sslContext.ifPresent(ssl -> ctx.pipeline().addFirst("ssl", newSslHandler(ssl, ctx.channel(), backend)));
            ctx.pipeline().remove(this);
            ctx.fireChannelActive();
        }
    }

    /**
     * Turns the protocol server pipeline into a bare byte pipe. Every inbound decoder / protocol handler
     * (the IMAP {@code requestDecoder} that would parse the relayed bytes into an {@code ImapMessage}, the
     * framer, the timeout and core handlers, ...) would otherwise consume or transform the raw stream, so
     * we drop them all and keep only the SSL handler (when TLS is in use) below our {@link RelayHandler}.
     */
    private static void stripProtocolStack(ChannelPipeline pipeline) {
        pipeline.names().stream()
            .filter(name -> pipeline.get(name) != null)
            .filter(name -> !HandlerConstants.SSL_HANDLER.equals(name))
            .forEach(name -> removeIfPresent(pipeline, name));
    }

    private static void removeIfPresent(ChannelPipeline pipeline, String name) {
        if (pipeline.get(name) != null) {
            pipeline.remove(name);
        }
    }

    /**
     * Forwards every inbound byte buffer to the peer channel, accounting the relayed volume, and
     * propagates connection close in both directions.
     */
    private static final class RelayHandler extends ChannelInboundHandlerAdapter {
        private final Channel peer;
        private final IntConsumer volumeRecorder;

        private RelayHandler(Channel peer, IntConsumer volumeRecorder) {
            this.peer = peer;
            this.volumeRecorder = volumeRecorder;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buffer = (ByteBuf) msg;
            volumeRecorder.accept(buffer.readableBytes());
            if (peer.isActive()) {
                peer.writeAndFlush(buffer);
            } else {
                buffer.release();
                ctx.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (peer.isActive()) {
                peer.flush();
                peer.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.debug("Relay error, closing channels", cause);
            ctx.close();
            if (peer.isActive()) {
                peer.close();
            }
        }
    }
}
