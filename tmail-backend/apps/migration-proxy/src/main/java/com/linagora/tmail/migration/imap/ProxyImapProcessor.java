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
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSaslExchangeTracker;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.AuthenticateRequest;
import org.apache.james.imap.message.request.CapabilityRequest;
import org.apache.james.imap.message.request.IRAuthenticateRequest;
import org.apache.james.imap.message.request.LoginRequest;
import org.apache.james.imap.message.request.LogoutRequest;
import org.apache.james.imap.message.request.NoopRequest;
import org.apache.james.imap.message.request.StartTLSRequest;
import org.apache.james.imapserver.netty.HAProxyMessageHandler;
import org.apache.james.protocols.api.ProxyInformation;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslCodec;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.migration.core.AdminCredentials;
import com.linagora.tmail.migration.core.Backend;
import com.linagora.tmail.migration.core.BackendDialog;
import com.linagora.tmail.migration.core.BackendRelay;
import com.linagora.tmail.migration.core.BackendResolver;
import com.linagora.tmail.migration.core.BackendSslContextFactory;
import com.linagora.tmail.migration.core.MissingProxyInformationException;
import com.linagora.tmail.migration.core.ProxyConnectionRegistry;
import com.linagora.tmail.migration.core.ReflectiveChannelAccessor;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The minimal IMAP stack the proxy needs: answer CAPABILITY, optionally negotiate STARTTLS (delegated
 * to James' {@link ImapSession#startTLS}) and authenticate the client, then hand the connection over to
 * {@link BackendRelay}. Everything else is rejected: once logged in, traffic is proxied raw.
 *
 * <p>Two authentication modes, mutually exclusive:
 * <ul>
 *   <li>without Kerberos, {@code LOGIN} captures the credentials and replays them against the backend;</li>
 *   <li>with Kerberos, {@code LOGIN} is disabled and {@code AUTHENTICATE GSSAPI} is the only way in. The
 *   proxy then never sees a user password and opens the backend session by delegation, as the configured
 *   backend administrator.</li>
 * </ul>
 */
public class ProxyImapProcessor implements ImapProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyImapProcessor.class);

    private final BackendResolver backendResolver;
    private final BackendRelay backendRelay;
    private final BackendSslContextFactory sslContextFactory;
    private final ProxyConnectionRegistry connectionRegistry;
    private final Duration handshakeTimeout;
    private final Optional<SaslMechanism> gssapi;
    private final SaslAuthenticator saslAuthenticator;

    public ProxyImapProcessor(BackendResolver backendResolver, BackendRelay backendRelay,
                              BackendSslContextFactory sslContextFactory,
                              ProxyConnectionRegistry connectionRegistry, Duration handshakeTimeout,
                              Optional<SaslMechanism> gssapi) {
        this.backendResolver = backendResolver;
        this.backendRelay = backendRelay;
        this.sslContextFactory = sslContextFactory;
        this.connectionRegistry = connectionRegistry;
        this.handshakeTimeout = handshakeTimeout;
        this.gssapi = gssapi;
        this.saslAuthenticator = new ProxySaslAuthenticator();
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
            proxyLogin(clientChannel, tag, login);
        } else if (message instanceof AuthenticateRequest authenticate) {
            proxyAuthenticate(session, clientChannel, tag, authenticate);
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

    private void proxyLogin(Channel clientChannel, String tag, LoginRequest login) {
        if (gssapi.isPresent()) {
            writeLine(clientChannel, tag + " NO LOGIN is disabled, use AUTHENTICATE GSSAPI.");
            return;
        }
        Username username = login.getUserid();
        relay(clientChannel, tag, username, backendResolver.resolve(username).block(),
            backend -> new ImapBackendDialog(username.asString(), login.getPassword()), "LOGIN");
    }

    private void proxyAuthenticate(ImapSession session, Channel clientChannel, String tag, AuthenticateRequest request) {
        Optional<SaslMechanism> mechanism = gssapi
            .filter(candidate -> candidate.name().equalsIgnoreCase(request.getAuthType()));
        if (mechanism.isEmpty()) {
            writeLine(clientChannel, tag + " NO Unsupported authentication mechanism.");
            return;
        }
        if (!mechanism.get().isAvailableOnTransport(session.isTLSActive())) {
            writeLine(clientChannel, tag + " NO " + mechanism.get().name()
                + " authentication requires an encrypted transport.");
            return;
        }

        startExchange(mechanism.get(), session, clientChannel, tag, request)
            .ifPresent(exchange -> handleFirstStep(exchange, session, clientChannel, tag));
    }

    private Optional<SaslExchange> startExchange(SaslMechanism mechanism, ImapSession session, Channel clientChannel,
                                                 String tag, AuthenticateRequest request) {
        try {
            return Optional.of(ImapSaslExchangeTracker.forSession(session)
                .register(mechanism.start(
                    SaslCodec.initialRequest(request.getAuthType(), initialClientResponse(request)),
                    saslAuthenticator)));
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid syntax in AUTHENTICATE initial client response", e);
            writeLine(clientChannel, tag + " BAD Malformed authentication command.");
            return Optional.empty();
        }
    }

    private void handleFirstStep(SaslExchange exchange, ImapSession session, Channel clientChannel, String tag) {
        SaslStep step = firstStep(exchange, session);
        if (step instanceof SaslStep.Challenge challenge) {
            // A single line handler drives the whole exchange: it stays pushed until a terminal step, so
            // that the base64 continuations are not parsed as IMAP commands.
            session.pushLineHandler((lineSession, data) -> continuation(exchange, lineSession, clientChannel, tag, data));
            writeChallenge(clientChannel, challenge);
            return;
        }
        handleTerminalStep(exchange, session, clientChannel, tag, step);
    }

    private SaslStep firstStep(SaslExchange exchange, ImapSession session) {
        try {
            return exchange.firstStep();
        } catch (RuntimeException e) {
            closeExchange(session, exchange);
            throw e;
        }
    }

    private Publisher<Void> continuation(SaslExchange exchange, ImapSession session, Channel clientChannel,
                                         String tag, byte[] data) {
        // Same reason as processReactive: the terminal step opens the backend connection and blocks.
        return Mono.fromRunnable(() -> onContinuationLine(exchange, session, clientChannel, tag, data))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    private void onContinuationLine(SaslExchange exchange, ImapSession session, Channel clientChannel,
                                    String tag, byte[] data) {
        if (SaslCodec.isAbort(data)) {
            session.popLineHandler();
            closeExchange(session, exchange);
            writeLine(clientChannel, tag + " NO AUTHENTICATE aborted.");
            return;
        }

        nextStep(exchange, session, clientChannel, tag, data).ifPresent(step -> {
            if (step instanceof SaslStep.Challenge challenge) {
                writeChallenge(clientChannel, challenge);
                return;
            }
            session.popLineHandler();
            handleTerminalStep(exchange, session, clientChannel, tag, step);
        });
    }

    private Optional<SaslStep> nextStep(SaslExchange exchange, ImapSession session, Channel clientChannel,
                                        String tag, byte[] data) {
        try {
            return Optional.of(exchange.onResponse(SaslCodec.decodeClientResponse(data)));
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid syntax in AUTHENTICATE client response", e);
            session.popLineHandler();
            closeExchange(session, exchange);
            writeLine(clientChannel, tag + " BAD Malformed authentication command.");
            return Optional.empty();
        }
    }

    private void handleTerminalStep(SaslExchange exchange, ImapSession session, Channel clientChannel,
                                    String tag, SaslStep step) {
        try {
            switch (step) {
                // GSSAPI never carries final server data (GssapiSaslExchange rejects any), so a success is
                // terminal: no extra continuation round trip to acknowledge it.
                case SaslStep.Success success -> relayAuthenticated(clientChannel, tag,
                    success.identity().authorizationId());
                case SaslStep.Failure failure -> {
                    LOGGER.info("AUTHENTICATE rejected: {} ({})", failure.failure().reason(), failure.failure().type());
                    writeLine(clientChannel, tag + " NO AUTHENTICATE failed.");
                }
                case SaslStep.Challenge ignored ->
                    throw new IllegalStateException("A challenge is not a terminal SASL step");
            }
        } finally {
            closeExchange(session, exchange);
        }
    }

    private void relayAuthenticated(Channel clientChannel, String tag, Username username) {
        Backend backend = backendResolver.resolve(username).block();
        AdminCredentials admin = backend.admin()
            .orElseThrow(() -> new IllegalStateException("No administrator configured for backend " + backend.name()));
        relay(clientChannel, tag, username, backend,
            resolved -> new ImapPlainImpersonationBackendDialog(username.asString(), admin), "AUTHENTICATE");
    }

    private void relay(Channel clientChannel, String tag, Username username, Backend backend,
                       Function<Backend, BackendDialog> dialogFactory, String command) {
        Optional<ProxyInformation> inboundProxyInfo =
            Optional.ofNullable(clientChannel.attr(HAProxyMessageHandler.PROXY_INFO).get());
        Supplier<BackendDialog> dialogSupplier = () -> dialogFactory.apply(backend);

        Optional<Channel> backendChannel;
        try {
            backendChannel = backendRelay.connectAndAuthenticate(clientChannel,
                new BackendRelay.RelayRequest(backend, dialogSupplier,
                    sslContextFactory.forBackend(backend), handshakeTimeout, inboundProxyInfo));
        } catch (MissingProxyInformationException e) {
            writeLine(clientChannel, tag + " NO Proxy protocol information required but missing.");
            return;
        }

        if (backendChannel.isPresent()) {
            writeLine(clientChannel, tag + " OK " + command + " completed, proxying to " + backend.name() + " backend.");
            backendRelay.takeOverClient(clientChannel, backendChannel.get(), backend);
            // Track the live session so it can be force-closed if this user gets migrated, forcing a
            // reconnection onto the (now) resolved backend rather than staying pinned to the old one.
            connectionRegistry.register(username, clientChannel);
        } else {
            writeLine(clientChannel, tag + " NO " + command + " failed against backend.");
        }
    }

    private void writeCapabilities(Channel clientChannel, ImapSession session, String tag) {
        StringBuilder capabilities = new StringBuilder("* CAPABILITY IMAP4rev1");
        if (session.supportStartTLS()) {
            capabilities.append(" STARTTLS");
        }
        if (gssapi.isPresent()) {
            // Kerberos is exclusive: the proxy has no way to check a password on its own any more.
            capabilities.append(" LOGINDISABLED");
            if (gssapi.get().isAvailableOnTransport(session.isTLSActive())) {
                capabilities.append(" AUTH=").append(gssapi.get().name()).append(" SASL-IR");
            }
        }
        // Without Kerberos we only implement the LOGIN command (not the AUTHENTICATE SASL flow), so we must
        // not advertise AUTH=PLAIN: a client picking AUTHENTICATE PLAIN over LOGIN would otherwise be rejected.
        writeLine(clientChannel, capabilities.toString());
        writeLine(clientChannel, tag + " OK CAPABILITY completed.");
    }

    private void startTls(ImapSession session, Channel clientChannel, String tag) {
        boolean started = session.startTLS(() -> writeLine(clientChannel, tag + " OK Begin TLS negotiation now."));
        if (!started) {
            writeLine(clientChannel, tag + " BAD STARTTLS is not supported.");
        }
    }

    private void writeChallenge(Channel clientChannel, SaslStep.Challenge challenge) {
        writeLine(clientChannel, ("+ " + SaslCodec.encode(challenge.payload())).stripTrailing());
    }

    private Optional<String> initialClientResponse(AuthenticateRequest request) {
        if (request instanceof IRAuthenticateRequest irRequest) {
            return Optional.of(irRequest.getInitialClientResponse());
        }
        return Optional.empty();
    }

    private void closeExchange(ImapSession session, SaslExchange exchange) {
        ImapSaslExchangeTracker.forSession(session).closeExchange(exchange);
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
