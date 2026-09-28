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


package com.linagora.tmail.migration.submission;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.api.handler.DisconnectHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.sasl.SaslCodec;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.esmtp.EhloExtension;
import org.apache.james.protocols.smtp.core.esmtp.LoginSaslMechanismFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.migration.imap.ProxySaslAuthenticator;

/**
 * {@code AUTH} for the submission proxy: runs the SASL exchange ({@code PLAIN}, or its {@code LOGIN}
 * framing) only to capture the credentials, which {@link SubmissionBackendHandover} replays against the
 * backend - the one authority on them. Delegation is refused: the proxy cannot tell which delegations a
 * backend would allow.
 */
public class SubmissionAuthCmdHandler implements CommandHandler<SMTPSession>, EhloExtension, DisconnectHandler<SMTPSession> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionAuthCmdHandler.class);
    private static final String COMMAND = "AUTH";
    private static final ProtocolSession.AttachmentKey<SaslExchange> ACTIVE_EXCHANGE =
        ProtocolSession.AttachmentKey.of("SUBMISSION_PROXY_SASL_EXCHANGE", SaslExchange.class);

    static final Response SYNTAX_ERROR = new SMTPResponse("501", "5.5.2 Usage: AUTH mechanism [initial-response]").immutable();
    static final Response MALFORMED = new SMTPResponse("501", "5.5.2 Cannot decode the authentication response").immutable();
    static final Response ABORTED = new SMTPResponse("501", "5.0.0 Authentication aborted").immutable();
    static final Response UNSUPPORTED_MECHANISM = new SMTPResponse("504", "5.5.4 Unrecognized authentication type").immutable();
    static final Response ENCRYPTION_REQUIRED = new SMTPResponse("538", "5.7.11 Encryption required for requested authentication mechanism").immutable();

    private final SubmissionBackendHandover handover;
    private final ImmutableList<SaslMechanism> mechanisms;

    @Inject
    public SubmissionAuthCmdHandler(SubmissionBackendHandover handover, SubmissionProxyConfiguration configuration) {
        this.handover = handover;
        this.mechanisms = mechanisms(configuration.requireSSL());
    }

    private static ImmutableList<SaslMechanism> mechanisms(boolean requireSSL) {
        SaslMechanism plain = new PlainSaslMechanism(PlainSaslMechanism.ENABLED, requireSSL);
        try {
            SaslMechanism login = new LoginSaslMechanismFactory(serverConfiguration -> plain)
                .create(new BaseHierarchicalConfiguration());
            return ImmutableList.of(plain, login);
        } catch (ConfigurationException e) {
            throw new IllegalStateException("The LOGIN framing of PLAIN takes no configuration", e);
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return ImmutableSet.of(COMMAND);
    }

    @Override
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        ImmutableList<String> names = availableMechanisms(session).stream()
            .map(SaslMechanism::name)
            .collect(ImmutableList.toImmutableList());
        if (names.isEmpty()) {
            return ImmutableList.of();
        }
        String joined = Joiner.on(' ').join(names);
        // The AUTH= form is still looked for by some legacy clients.
        return ImmutableList.of("AUTH " + joined, "AUTH=" + joined);
    }

    @Override
    public Response onCommand(SMTPSession session, Request request) {
        String argument = request.getArgument();
        if (argument == null || argument.isBlank()) {
            return SYNTAX_ERROR;
        }
        String[] parts = argument.trim().split(" ", 2);
        String mechanismName = parts[0].toUpperCase(Locale.US);
        Optional<String> initialResponse = parts.length > 1 ? Optional.of(parts[1].trim()) : Optional.empty();

        Optional<SaslMechanism> mechanism = mechanisms.stream()
            .filter(candidate -> candidate.name().equals(mechanismName))
            .findFirst();
        if (mechanism.isEmpty()) {
            return UNSUPPORTED_MECHANISM;
        }
        if (!mechanism.get().isAvailableOnTransport(session.isTLSStarted())) {
            return ENCRYPTION_REQUIRED;
        }
        return start(session, mechanism.get(), initialResponse);
    }

    private Response start(SMTPSession session, SaslMechanism mechanism, Optional<String> initialResponse) {
        ProxySaslAuthenticator authenticator = new ProxySaslAuthenticator();
        SaslExchange exchange;
        try {
            exchange = mechanism.start(SaslCodec.initialRequest(mechanism.name(), initialResponse), authenticator);
        } catch (IllegalArgumentException e) {
            LOGGER.info("Could not decode the AUTH {} initial response", mechanism.name(), e);
            return MALFORMED;
        }
        register(session, exchange);
        try {
            SaslStep step = exchange.firstStep();
            if (step instanceof SaslStep.Challenge challenge) {
                session.pushLineHandler(continuation(exchange, authenticator));
                return challengeResponse(challenge);
            }
            return terminate(session, authenticator, step);
        } catch (RuntimeException e) {
            close(session);
            throw e;
        }
    }

    /**
     * Pushed while a challenge is outstanding, so that the client response is not parsed as a command.
     */
    private LineHandler<SMTPSession> continuation(SaslExchange exchange, ProxySaslAuthenticator authenticator) {
        return (session, line) -> {
            if (SaslCodec.isAbort(line)) {
                leaveSaslMode(session);
                return ABORTED;
            }
            SaslStep step;
            try {
                step = exchange.onResponse(SaslCodec.decodeClientResponse(line));
            } catch (IllegalArgumentException e) {
                LOGGER.info("Could not decode an AUTH client response", e);
                leaveSaslMode(session);
                return MALFORMED;
            } catch (RuntimeException e) {
                leaveSaslMode(session);
                throw e;
            }
            if (step instanceof SaslStep.Challenge challenge) {
                return challengeResponse(challenge);
            }
            // Terminal step: leave SASL mode, whatever the outcome of the handover.
            session.popLineHandler();
            return terminate(session, authenticator, step);
        };
    }

    private static void leaveSaslMode(SMTPSession session) {
        session.popLineHandler();
        close(session);
    }

    private Response terminate(SMTPSession session, ProxySaslAuthenticator authenticator, SaslStep step) {
        try {
            return switch (step) {
                // PLAIN never carries final server data: a success is terminal.
                case SaslStep.Success success -> handover.handover(session, success.identity().authorizationId(),
                    authenticator.capturedPassword()
                        .orElseThrow(() -> new IllegalStateException("PLAIN completed without a password")));
                case SaslStep.Failure failure -> {
                    LOGGER.info("AUTH rejected by the submission proxy: {} ({})", failure.failure().reason(), failure.failure().type());
                    yield switch (failure.failure().type()) {
                        case MALFORMED -> MALFORMED;
                        case SERVER_ERROR -> SubmissionBackendHandover.TEMPORARY_FAILURE;
                        case INVALID_CREDENTIALS, AUTHENTICATION_FAILED, USER_DOES_NOT_EXIST, DELEGATION_FORBIDDEN ->
                            SubmissionBackendHandover.CREDENTIALS_REJECTED;
                    };
                }
                case SaslStep.Challenge ignored -> throw new IllegalStateException("A challenge is not a terminal SASL step");
            };
        } finally {
            close(session);
        }
    }

    private static Response challengeResponse(SaslStep.Challenge challenge) {
        return new SMTPResponse("334", SaslCodec.encode(challenge.payload()));
    }

    private static void register(SMTPSession session, SaslExchange exchange) {
        session.setAttachment(ACTIVE_EXCHANGE, exchange, ProtocolSession.State.Connection)
            .ifPresent(SaslExchange::close);
    }

    private static void close(SMTPSession session) {
        session.removeAttachment(ACTIVE_EXCHANGE, ProtocolSession.State.Connection)
            .ifPresent(SaslExchange::close);
    }

    private ImmutableList<SaslMechanism> availableMechanisms(SMTPSession session) {
        return mechanisms.stream()
            .filter(mechanism -> mechanism.isAvailableOnTransport(session.isTLSStarted()))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public void onDisconnect(SMTPSession session) {
        if (session != null) {
            close(session);
        }
    }
}
