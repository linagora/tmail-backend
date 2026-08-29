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
import org.apache.james.protocols.api.sasl.SaslCodec;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;
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
 *   <li>without Kerberos, {@code LOGIN} and {@code AUTHENTICATE PLAIN} both capture the user password
 *   and replay it against the resolved backend, which is the only authority on it;</li>
 *   <li>with Kerberos, both are disabled and {@code AUTHENTICATE GSSAPI} is the only way in. The proxy
 *   then never sees a user password and opens the backend session by delegation, as the configured
 *   backend administrator.</li>
 * </ul>
 */
public class ProxyImapProcessor implements ImapProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyImapProcessor.class);
    // The proxy relays LOGIN in clear text whenever the client sends it, so requiring TLS for PLAIN - which
    // carries the very same credentials - would only push clients back onto the weaker command.
    private static final boolean REQUIRES_SSL = false;
    private static final SaslMechanism PLAIN = new PlainSaslMechanism(PlainSaslMechanism.ENABLED, REQUIRES_SSL);

    private final BackendResolver backendResolver;
    private final BackendRelay backendRelay;
    private final BackendSslContextFactory sslContextFactory;
    private final ProxyConnectionRegistry connectionRegistry;
    private final Duration handshakeTimeout;
    private final Optional<SaslMechanism> gssapi;
    private final SaslMechanism saslMechanism;

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
        // Kerberos is exclusive: it is offered precisely because the password must not transit any more.
        this.saslMechanism = gssapi.orElse(PLAIN);
    }

    /**
     * One {@code AUTHENTICATE} command in flight, from the initial request to the terminal SASL step.
     */
    private record PendingAuthentication(SaslExchange exchange, ProxySaslAuthenticator authenticator,
                                         Channel clientChannel, String tag) {
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

        try {
            dispatch(message, session, clientChannel, tag);
        } catch (RuntimeException e) {
            // Nothing downstream answers for us: an unanswered command leaves the client hanging until its
            // own timeout, so every failure has to become a tagged response here.
            LOGGER.error("Unexpected error while handling {}", request.getCommand().getName(), e);
            writeLine(clientChannel, tag + " NO Internal error.");
        }
    }

    private void dispatch(ImapMessage message, ImapSession session, Channel clientChannel, String tag) {
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
            () -> new ImapBackendDialog(username.asString(), login.getPassword()), "LOGIN");
    }

    private void proxyAuthenticate(ImapSession session, Channel clientChannel, String tag, AuthenticateRequest request) {
        if (!saslMechanism.name().equalsIgnoreCase(request.getAuthType())) {
            writeLine(clientChannel, tag + " NO Unsupported authentication mechanism.");
            return;
        }
        if (!saslMechanism.isAvailableOnTransport(session.isTLSActive())) {
            writeLine(clientChannel, tag + " NO " + saslMechanism.name()
                + " authentication requires an encrypted transport.");
            return;
        }

        startExchange(session, clientChannel, tag, request)
            .ifPresent(pending -> handleFirstStep(pending, session));
    }

    private Optional<PendingAuthentication> startExchange(ImapSession session, Channel clientChannel, String tag,
                                                          AuthenticateRequest request) {
        ProxySaslAuthenticator authenticator = new ProxySaslAuthenticator();
        try {
            SaslExchange exchange = ImapSaslExchangeTracker.forSession(session)
                .register(saslMechanism.start(
                    SaslCodec.initialRequest(request.getAuthType(), initialClientResponse(request)),
                    authenticator));
            return Optional.of(new PendingAuthentication(exchange, authenticator, clientChannel, tag));
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid syntax in AUTHENTICATE initial client response", e);
            writeLine(clientChannel, tag + " BAD Malformed authentication command.");
            return Optional.empty();
        }
    }

    private void handleFirstStep(PendingAuthentication pending, ImapSession session) {
        SaslStep step = firstStep(pending, session);
        if (step instanceof SaslStep.Challenge challenge) {
            // A single line handler drives the whole exchange: it stays pushed until a terminal step, so
            // that the base64 continuations are not parsed as IMAP commands.
            session.pushLineHandler((lineSession, data) -> continuation(pending, lineSession, data));
            writeChallenge(pending.clientChannel(), challenge);
            return;
        }
        handleTerminalStep(pending, session, step);
    }

    private SaslStep firstStep(PendingAuthentication pending, ImapSession session) {
        try {
            return pending.exchange().firstStep();
        } catch (RuntimeException e) {
            closeExchange(session, pending);
            throw e;
        }
    }

    private Publisher<Void> continuation(PendingAuthentication pending, ImapSession session, byte[] data) {
        // Same reason as processReactive: the terminal step opens the backend connection and blocks.
        return Mono.fromRunnable(() -> onContinuationLine(pending, session, data))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    private void onContinuationLine(PendingAuthentication pending, ImapSession session, byte[] data) {
        try {
            continueExchange(pending, session, data);
        } catch (RuntimeException e) {
            // Same reason as handle(): an unanswered continuation leaves the client hanging.
            LOGGER.error("Unexpected error while continuing the AUTHENTICATE exchange", e);
            closeExchange(session, pending);
            writeLine(pending.clientChannel(), pending.tag() + " NO Internal error.");
        }
    }

    private void continueExchange(PendingAuthentication pending, ImapSession session, byte[] data) {
        if (SaslCodec.isAbort(data)) {
            session.popLineHandler();
            closeExchange(session, pending);
            writeLine(pending.clientChannel(), pending.tag() + " NO AUTHENTICATE aborted.");
            return;
        }

        nextStep(pending, session, data).ifPresent(step -> {
            if (step instanceof SaslStep.Challenge challenge) {
                writeChallenge(pending.clientChannel(), challenge);
                return;
            }
            session.popLineHandler();
            handleTerminalStep(pending, session, step);
        });
    }

    private Optional<SaslStep> nextStep(PendingAuthentication pending, ImapSession session, byte[] data) {
        try {
            return Optional.of(pending.exchange().onResponse(SaslCodec.decodeClientResponse(data)));
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid syntax in AUTHENTICATE client response", e);
            session.popLineHandler();
            closeExchange(session, pending);
            writeLine(pending.clientChannel(), pending.tag() + " BAD Malformed authentication command.");
            return Optional.empty();
        } catch (RuntimeException e) {
            // Leave the session out of SASL mode before onContinuationLine turns this into a tagged NO.
            session.popLineHandler();
            throw e;
        }
    }

    private void handleTerminalStep(PendingAuthentication pending, ImapSession session, SaslStep step) {
        try {
            switch (step) {
                // GSSAPI never carries final server data (GssapiSaslExchange rejects any) and PLAIN has none,
                // so a success is terminal: no extra continuation round trip to acknowledge it.
                case SaslStep.Success success -> relayAuthenticated(pending, success.identity().authorizationId());
                case SaslStep.Failure failure -> {
                    LOGGER.info("AUTHENTICATE rejected: {} ({})", failure.failure().reason(), failure.failure().type());
                    writeLine(pending.clientChannel(), pending.tag() + " NO AUTHENTICATE failed.");
                }
                case SaslStep.Challenge ignored ->
                    throw new IllegalStateException("A challenge is not a terminal SASL step");
            }
        } finally {
            closeExchange(session, pending);
        }
    }

    private void relayAuthenticated(PendingAuthentication pending, Username username) {
        Backend backend = backendResolver.resolve(username).block();
        relay(pending.clientChannel(), pending.tag(), username, backend,
            backendDialog(pending.authenticator(), username, backend), "AUTHENTICATE");
    }

    /**
     * PLAIN captured the user password, which we replay as a backend LOGIN. GSSAPI captured none: the
     * backend session is then opened by delegation from the configured administrator.
     */
    private Supplier<BackendDialog> backendDialog(ProxySaslAuthenticator authenticator, Username username,
                                                  Backend backend) {
        return authenticator.capturedPassword()
            .<Supplier<BackendDialog>>map(password -> () -> new ImapBackendDialog(username.asString(), password))
            .orElseGet(() -> {
                AdminCredentials admin = backend.admin().orElseThrow(() ->
                    new IllegalStateException("No administrator configured for backend " + backend.name()));
                return () -> new ImapPlainImpersonationBackendDialog(username.asString(), admin);
            });
    }

    private void relay(Channel clientChannel, String tag, Username username, Backend backend,
                       Supplier<BackendDialog> dialogFactory, String command) {
        Optional<ProxyInformation> inboundProxyInfo =
            Optional.ofNullable(clientChannel.attr(HAProxyMessageHandler.PROXY_INFO).get());

        Optional<Channel> backendChannel;
        try {
            backendChannel = backendRelay.connectAndAuthenticate(clientChannel,
                new BackendRelay.RelayRequest(backend, dialogFactory,
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
        }
        if (saslMechanism.isAvailableOnTransport(session.isTLSActive())) {
            capabilities.append(" AUTH=").append(saslMechanism.name()).append(" SASL-IR");
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

    private void writeChallenge(Channel clientChannel, SaslStep.Challenge challenge) {
        writeLine(clientChannel, ("+ " + SaslCodec.encode(challenge.payload())).stripTrailing());
    }

    private Optional<String> initialClientResponse(AuthenticateRequest request) {
        if (request instanceof IRAuthenticateRequest irRequest) {
            return Optional.of(irRequest.getInitialClientResponse());
        }
        return Optional.empty();
    }

    private void closeExchange(ImapSession session, PendingAuthentication pending) {
        ImapSaslExchangeTracker.forSession(session).closeExchange(pending.exchange());
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
