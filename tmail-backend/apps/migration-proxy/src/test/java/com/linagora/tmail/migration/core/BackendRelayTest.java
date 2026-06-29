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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.ProxyInformation;
import org.apache.james.util.Host;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.migration.StubBackendServer;
import com.linagora.tmail.migration.imap.ImapBackendDialog;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;

class BackendRelayTest {
    private static final String LOGIN_PREFIX = ImapBackendDialog.PROXY_TAG + " LOGIN";

    private StubBackendServer backend;
    private EventLoopGroup clientFacingGroup;
    private Channel clientFacingServer;
    private final CompletableFuture<Channel> acceptedClientChannel = new CompletableFuture<>();
    private BackendRelay relay;

    @BeforeEach
    void setUp() {
        relay = new BackendRelay(new MigrationProxyMetrics(new RecordingMetricFactory()));
        clientFacingGroup = new NioEventLoopGroup(1);
    }

    @AfterEach
    void tearDown() {
        if (clientFacingServer != null) {
            clientFacingServer.close().awaitUninterruptibly();
        }
        clientFacingGroup.shutdownGracefully();
        if (backend != null) {
            backend.close();
        }
    }

    @Test
    void shouldAuthenticateBackendThenPipeBothDirections() throws Exception {
        backend = new StubBackendServer("* OK backend ready")
            .reply(LOGIN_PREFIX, ImapBackendDialog.PROXY_TAG + " OK LOGIN completed");
        int backendPort = backend.start();
        int clientFacingPort = startClientFacingServer();

        try (Socket clientSocket = new Socket("127.0.0.1", clientFacingPort)) {
            Channel clientChannel = acceptedClientChannel.get();

            Optional<Channel> backendChannel = relay.connectAndAuthenticate(clientChannel,
                new Backend("new", Host.from("127.0.0.1", backendPort), false, false, false), Protocol.IMAP,
                () -> new ImapBackendDialog("bob@domain.tld", "secret"),
                Optional.empty(), Duration.ofSeconds(10), Optional.empty());

            assertThat(backendChannel).isPresent();
            await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(backend.receivedLines()).anyMatch(line -> line.startsWith(LOGIN_PREFIX)));

            relay.takeOverClient(clientChannel, backendChannel.get(),
                Protocol.IMAP, new Backend("new", Host.from("127.0.0.1", backendPort), false, false, false));

            // client -> backend
            OutputStream out = clientSocket.getOutputStream();
            out.write("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(backend.receivedLines()).contains("a1 SELECT INBOX"));

            // backend -> client
            backend.pushToClient("a1 OK SELECT completed\r\n");
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            assertThat(reader.readLine()).isEqualTo("a1 OK SELECT completed");
        }
    }

    @Test
    void shouldForwardInboundProxyInfoWhenEnabled() throws Exception {
        backend = new StubBackendServer("* OK backend ready")
            .reply(LOGIN_PREFIX, ImapBackendDialog.PROXY_TAG + " OK LOGIN completed");
        int backendPort = backend.start();
        int clientFacingPort = startClientFacingServer();

        ProxyInformation inboundProxyInfo = new ProxyInformation(
            new InetSocketAddress("10.0.0.9", 54321),
            new InetSocketAddress("10.0.0.1", 143));

        try (Socket clientSocket = new Socket("127.0.0.1", clientFacingPort)) {
            Channel clientChannel = acceptedClientChannel.get();

            Optional<Channel> backendChannel = relay.connectAndAuthenticate(clientChannel,
                new Backend("new", Host.from("127.0.0.1", backendPort), false, false, true), Protocol.IMAP,
                () -> new ImapBackendDialog("bob@domain.tld", "secret"),
                Optional.empty(), Duration.ofSeconds(10), Optional.of(inboundProxyInfo));

            assertThat(backendChannel).isPresent();
            await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(backend.receivedLines())
                    .contains("PROXY TCP4 10.0.0.9 10.0.0.1 54321 143"));
        }
    }

    @Test
    void shouldFailWhenForwardProxyInfoButNoInboundInfo() throws Exception {
        backend = new StubBackendServer("* OK backend ready");
        int backendPort = backend.start();
        int clientFacingPort = startClientFacingServer();

        try (Socket clientSocket = new Socket("127.0.0.1", clientFacingPort)) {
            Channel clientChannel = acceptedClientChannel.get();
            Backend forwardingBackend = new Backend("new", Host.from("127.0.0.1", backendPort), false, false, true);

            assertThatThrownBy(() -> relay.connectAndAuthenticate(clientChannel, forwardingBackend, Protocol.IMAP,
                    () -> new ImapBackendDialog("bob@domain.tld", "secret"),
                    Optional.empty(), Duration.ofSeconds(10), Optional.empty()))
                .isInstanceOf(MissingProxyInformationException.class);
        }
    }

    private int startClientFacingServer() throws InterruptedException {
        clientFacingServer = new ServerBootstrap()
            .group(clientFacingGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    // Mimic the James protocol pipeline the relay strips on takeover.
                    channel.pipeline().addLast("framer", new LineBasedFrameDecoder(65536, false, false));
                    channel.pipeline().addLast("coreHandler", new ChannelInboundHandlerAdapter());
                    acceptedClientChannel.complete(channel);
                }
            })
            .bind(new InetSocketAddress("127.0.0.1", 0))
            .sync()
            .channel();
        return ((InetSocketAddress) clientFacingServer.localAddress()).getPort();
    }
}
