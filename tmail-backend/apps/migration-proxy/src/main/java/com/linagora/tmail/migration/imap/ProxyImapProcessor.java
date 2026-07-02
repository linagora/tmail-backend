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

package com.linagora.tmail.migration.imap;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.CapabilityRequest;
import org.apache.james.imap.message.request.LoginRequest;
import org.apache.james.imap.message.request.LogoutRequest;
import org.apache.james.imap.message.request.NoopRequest;
import org.apache.james.imap.message.request.StartTLSRequest;
import org.apache.james.imapserver.netty.HAProxyMessageHandler;
import org.apache.james.protocols.api.ProxyInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.migration.core.Backend;
import com.linagora.tmail.migration.core.BackendRelay;
import com.linagora.tmail.migration.core.BackendResolver;
import com.linagora.tmail.migration.core.BackendSslContextFactory;
import com.linagora.tmail.migration.core.MissingProxyInformationException;
import com.linagora.tmail.migration.core.Protocol;
import com.linagora.tmail.migration.core.ReflectiveChannelAccessor;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The minimal IMAP stack the proxy needs: answer CAPABILITY, optionally negotiate STARTTLS (delegated
 * to James' {@link ImapSession#startTLS}) and, on LOGIN, capture the credentials, authenticate against
 * the backend and hand the connection over to {@link BackendRelay}. Everything else is rejected: once
 * logged in, traffic is proxied raw.
 */
public class ProxyImapProcessor implements ImapProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyImapProcessor.class);
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(30);

    private final BackendResolver backendResolver;
    private final BackendRelay backendRelay;
    private final BackendSslContextFactory sslContextFactory;

    public ProxyImapProcessor(BackendResolver backendResolver, BackendRelay backendRelay,
                              BackendSslContextFactory sslContextFactory) {
        this.backendResolver = backendResolver;
        this.backendRelay = backendRelay;
        this.sslContextFactory = sslContextFactory;
    }

    @Override
    public Mono<Void> processReactive(ImapMessage message, Responder responder, ImapSession session) {
        // The relay handshake blocks (it connects + authenticates against the backend), so it must run
        // off the IMAP event loop, which the backend connection itself uses.
        return Mono.fromRunnable(() -> handle(message, session))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    @Override
    public void process(ImapMessage message, Responder responder, ImapSession session) {
        handle(message, session);
    }

    private void handle(ImapMessage message, ImapSession session) {
        Channel clientChannel = ReflectiveChannelAccessor.extract(session);
        if (!(message instanceof ImapRequest request)) {
            return;
        }
        String tag = Optional.ofNullable(request.getTag()).map(Tag::asString).orElse("*");

        if (message instanceof LoginRequest login) {
            proxyLogin(session, clientChannel, tag, login);
        } else if (message instanceof CapabilityRequest) {
            writeCapabilities(clientChannel, session, tag);
        } else if (message instanceof StartTLSRequest) {
            startTls(session, clientChannel, tag);
        } else if (message instanceof NoopRequest) {
            writeLine(clientChannel, tag + " OK NOOP completed.");
        } else if (message instanceof LogoutRequest) {
            writeLine(clientChannel, "* BYE Logging out");
            writeLine(clientChannel, tag + " OK LOGOUT completed.");
            clientChannel.close();
        } else {
            writeLine(clientChannel, tag + " BAD Command not available on the migration proxy before LOGIN.");
        }
    }

    private void proxyLogin(ImapSession session, Channel clientChannel, String tag, LoginRequest login) {
        Backend backend = backendResolver.resolve(login.getUserid()).block();
        Optional<ProxyInformation> inboundProxyInfo =
            Optional.ofNullable(clientChannel.attr(HAProxyMessageHandler.PROXY_INFO).get());

        Optional<Channel> backendChannel;
        try {
            backendChannel = backendRelay.connectAndAuthenticate(clientChannel,
                new BackendRelay.RelayRequest(backend, Protocol.IMAP,
                    () -> new ImapBackendDialog(login.getUserid().asString(), login.getPassword()),
                    sslContextFactory.forBackend(backend), HANDSHAKE_TIMEOUT, inboundProxyInfo));
        } catch (MissingProxyInformationException e) {
            writeLine(clientChannel, tag + " NO Proxy protocol information required but missing.");
            return;
        }

        if (backendChannel.isPresent()) {
            writeLine(clientChannel, tag + " OK LOGIN completed, proxying to " + backend.name() + " backend.");
            backendRelay.takeOverClient(clientChannel, backendChannel.get(), Protocol.IMAP, backend);
        } else {
            writeLine(clientChannel, tag + " NO LOGIN failed against backend.");
        }
    }

    private void writeCapabilities(Channel clientChannel, ImapSession session, String tag) {
        StringBuilder capabilities = new StringBuilder("* CAPABILITY IMAP4rev1 AUTH=PLAIN");
        if (session.supportStartTLS()) {
            capabilities.append(" STARTTLS");
        }
        writeLine(clientChannel, capabilities.toString());
        writeLine(clientChannel, tag + " OK CAPABILITY completed.");
    }

    private void startTls(ImapSession session, Channel clientChannel, String tag) {
        boolean started = session.startTLS(() -> writeLine(clientChannel, tag + " OK Begin TLS negotiation now."));
        if (!started) {
            writeLine(clientChannel, tag + " BAD STARTTLS is not supported.");
        }
    }

    private void writeLine(Channel clientChannel, String line) {
        if (clientChannel.isActive()) {
            clientChannel.writeAndFlush(Unpooled.copiedBuffer(line + "\r\n", StandardCharsets.UTF_8));
        }
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {
        LOGGER.debug("Configuring migration proxy IMAP processor");
    }
}
