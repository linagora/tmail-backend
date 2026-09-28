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

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.ProtocolSessionImpl;
import org.apache.james.protocols.api.ProxyInformation;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.netty.HandlerConstants;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.migration.core.Backend;
import com.linagora.tmail.migration.core.BackendRelay;
import com.linagora.tmail.migration.core.BackendResolver;
import com.linagora.tmail.migration.core.BackendSslContextFactory;
import com.linagora.tmail.migration.core.MissingProxyInformationException;
import com.linagora.tmail.migration.core.ProxyConnectionRegistry;
import com.linagora.tmail.migration.core.ReflectiveChannelAccessor;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.EventExecutor;

/**
 * Once a submission client completed its SASL exchange with the proxy, authenticates against the
 * submission port of the backend the user belongs to and turns the client connection into a raw relay
 * towards it. The backend is the only authority on the password: the proxy never checks it.
 *
 * <p>The client may legitimately pipeline commands behind a single round-trip {@code AUTH PLAIN}
 * (RFC 4954 section 4). Those bytes were already read, possibly framed into lines the SMTP stack has
 * not dispatched yet, when the backend handshake completes. The handover therefore happens in the order
 * the bytes arrived:
 * <ol>
 *     <li>the {@code 235} reply is returned to the SMTP stack, which writes it before anything the
 *     backend could answer to relayed commands;</li>
 *     <li>a forwarder inserted in front of the SMTP core handler relays, verbatim, every line the framer
 *     still dispatches;</li>
 *     <li>once the current read is fully dispatched, a task queued on the SMTP handlers executor drops the
 *     framer - which hands its partially received line over to that same forwarder - and only then lets
 *     {@link BackendRelay#takeOverClient} turn the connection into a bare byte pipe.</li>
 * </ol>
 */
public class SubmissionBackendHandover {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionBackendHandover.class);
    private static final String PIPELINED_BYTES_FORWARDER = "submissionPipelinedBytesForwarder";

    static final Response AUTHENTICATION_SUCCEEDED = new SMTPResponse("235", "2.7.0 Authentication successful").immutable();
    static final Response CREDENTIALS_REJECTED = new SMTPResponse("535", "5.7.8 Authentication credentials invalid").immutable();
    static final Response TEMPORARY_FAILURE = new SMTPResponse("454", "4.7.0 Temporary authentication failure").immutable();

    private final BackendResolver backendResolver;
    private final BackendRelay backendRelay;
    private final BackendSslContextFactory sslContextFactory;
    private final ProxyConnectionRegistry connectionRegistry;
    private final SubmissionProxyConfiguration configuration;

    public SubmissionBackendHandover(BackendResolver backendResolver, BackendRelay backendRelay,
                                     BackendSslContextFactory sslContextFactory,
                                     ProxyConnectionRegistry connectionRegistry,
                                     SubmissionProxyConfiguration configuration) {
        this.backendResolver = backendResolver;
        this.backendRelay = backendRelay;
        this.sslContextFactory = sslContextFactory;
        this.connectionRegistry = connectionRegistry;
        this.configuration = configuration;
    }

    /**
     * Blocks while connecting to and authenticating against the backend: to be called from the SMTP
     * handlers executor, never from the client channel event loop the backend connection runs on.
     *
     * @return the reply to send to the client: {@code 235} once the relay is set up, an error otherwise
     */
    public Response handover(SMTPSession session, Username username, String password) {
        Channel clientChannel = clientChannel(session);
        Backend backend;
        try {
            backend = backendResolver.resolveTarget(username)
                .map(configuration::backend)
                .block();
        } catch (RuntimeException e) {
            LOGGER.error("Could not resolve the submission backend of {}", username.asString(), e);
            return TEMPORARY_FAILURE;
        }

        SmtpBackendDialog dialog = new SmtpBackendDialog(session.getConfiguration().getHelloName(),
            username.asString(), password);
        Optional<Channel> backendChannel;
        try {
            backendChannel = backendRelay.connectAndAuthenticate(clientChannel,
                new BackendRelay.RelayRequest(backend, () -> dialog, sslContextFactory.forBackend(backend),
                    configuration.handshakeTimeout(), proxyInformation(session)));
        } catch (MissingProxyInformationException e) {
            LOGGER.error("Cannot relay the submission of {}", username.asString(), e);
            return TEMPORARY_FAILURE;
        }

        if (backendChannel.isEmpty()) {
            return failure(dialog.outcome(), username, backend);
        }
        handOver(clientChannel, backendChannel.get(), backend);
        // Tracked so that migrating the user closes the session, pinned to the backend resolved at AUTH time.
        connectionRegistry.register(username, clientChannel);
        return AUTHENTICATION_SUCCEEDED;
    }

    private Response failure(SmtpBackendDialog.Outcome outcome, Username username, Backend backend) {
        if (outcome == SmtpBackendDialog.Outcome.CREDENTIALS_REJECTED) {
            LOGGER.info("The {} submission backend rejected the credentials of {}", backend.name(), username.asString());
            return CREDENTIALS_REJECTED;
        }
        LOGGER.warn("Could not authenticate {} against the {} submission backend ({})",
            username.asString(), backend.name(), outcome);
        return TEMPORARY_FAILURE;
    }

    private void handOver(Channel clientChannel, Channel backendChannel, Backend backend) {
        // The backend side relay closes the client when the backend goes away: close the backend when the
        // client goes away, including before the client side relay is installed.
        clientChannel.closeFuture().addListener(future -> backendChannel.close());
        ChannelPipeline pipeline = clientChannel.pipeline();
        EventExecutor protocolExecutor = pipeline.context(HandlerConstants.CORE_HANDLER).executor();
        // Running on that executor, the forwarder is in place before the framer dispatches its next line.
        pipeline.addBefore(protocolExecutor, HandlerConstants.CORE_HANDLER, PIPELINED_BYTES_FORWARDER,
            new PipelinedBytesForwarder(backendChannel));
        protocolExecutor.execute(() -> switchToRawRelay(clientChannel, backendChannel, backend));
    }

    private void switchToRawRelay(Channel clientChannel, Channel backendChannel, Backend backend) {
        try {
            ChannelPipeline pipeline = clientChannel.pipeline();
            if (pipeline.get(HandlerConstants.FRAMER) != null) {
                // Running on the framer executor, the removal is synchronous: the bytes of a line it did not
                // complete yet reach the forwarder before any byte read afterwards.
                pipeline.remove(HandlerConstants.FRAMER);
            }
            backendRelay.takeOverClient(clientChannel, backendChannel, backend);
        } catch (RuntimeException e) {
            LOGGER.error("Failed switching the submission connection to a raw relay towards {}", backend, e);
            clientChannel.close();
        }
    }

    /**
     * Relays verbatim what the SMTP stack read from the client but had not dispatched yet when the handover
     * happened: the lines pipelined behind {@code AUTH}, then the partial line the framer still buffers.
     * Buffers are passed along as they are, reader index included.
     */
    private static final class PipelinedBytesForwarder extends ChannelInboundHandlerAdapter {
        private final Channel backendChannel;

        private PipelinedBytesForwarder(Channel backendChannel) {
            this.backendChannel = backendChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            backendChannel.writeAndFlush(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            // Swallowed: the SMTP core handler must not react to reads it no longer owns.
        }
    }

    /**
     * {@link SMTPSession#getProxyInformation()} wraps a missing value with {@code Optional.of} and throws
     * when the connection carried no PROXY protocol header.
     */
    private static Optional<ProxyInformation> proxyInformation(SMTPSession session) {
        try {
            return session.getProxyInformation();
        } catch (NullPointerException e) {
            return Optional.empty();
        }
    }

    private static Channel clientChannel(SMTPSession session) {
        return ReflectiveChannelAccessor.extract(((ProtocolSessionImpl) session).getProtocolTransport());
    }
}
