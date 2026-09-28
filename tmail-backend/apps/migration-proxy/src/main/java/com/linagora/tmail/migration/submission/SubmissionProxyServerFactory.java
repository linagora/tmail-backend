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

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.smtpserver.netty.SMTPServer;
import org.apache.james.smtpserver.netty.SMTPServerFactory;
import org.apache.james.smtpserver.netty.SmtpMetrics;

import com.google.common.collect.ImmutableList;

/**
 * The listeners of the submission proxy, declared in {@code submissionproxy.xml} with the syntax of
 * {@code smtpserver.xml}: James' SMTP server brings the TLS, STARTTLS, PROXY protocol and connection
 * limit handling, while the command set is pinned to {@link SubmissionProxyHandlersLoader} - the full
 * MTA command set must never be reachable on these ports.
 */
public class SubmissionProxyServerFactory extends SMTPServerFactory {
    public static final String CONFIGURATION_NAME = "submissionproxy";
    private static final String CORE_HANDLERS_PACKAGE = "handlerchain[@coreHandlersPackage]";

    /**
     * The James AUTH command handler, the only consumer of the SASL settings of an SMTP server, is not part
     * of the submission proxy command set: {@link SubmissionAuthCmdHandler} does not rely on them.
     */
    private static final SaslAuthenticator NO_LOCAL_AUTHENTICATION = new SaslAuthenticator() {
        @Override
        public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId,
                                                             String password) {
            return new SaslAuthenticationResult.Failure(SaslFailure.authenticationFailed(Optional.of(authenticationId),
                authorizationId, "The submission proxy does not authenticate users locally"));
        }

        @Override
        public SaslAuthenticationResult authorize(SaslIdentity identity) {
            return new SaslAuthenticationResult.Failure(SaslFailure.authenticationFailed(Optional.of(identity.authenticationId()),
                Optional.of(identity.authorizationId()), "The submission proxy does not authenticate users locally"));
        }
    };

    private static class SubmissionProxyServer extends SMTPServer {
        SubmissionProxyServer(SmtpMetrics smtpMetrics) {
            super(smtpMetrics);
        }

        @Override
        protected Class<? extends HandlersPackage> getCoreHandlersPackage() {
            return SubmissionProxyHandlersLoader.class;
        }
    }

    @Inject
    public SubmissionProxyServerFactory(DNSService dns, ProtocolHandlerLoader loader, FileSystem fileSystem,
                                        MetricFactory metricFactory) {
        super(dns, loader, fileSystem, metricFactory, configuration -> ImmutableList.of(), NO_LOCAL_AUTHENTICATION);
    }

    @Override
    protected SMTPServer createServer() {
        return new SubmissionProxyServer(smtpMetrics);
    }

    @Override
    protected List<AbstractConfigurableAsyncServer> createServers(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        ImmutableList.Builder<AbstractConfigurableAsyncServer> servers = ImmutableList.builder();
        for (HierarchicalConfiguration<ImmutableNode> serverConfig : config.configurationsAt("smtpserver")) {
            pinCommandSet(serverConfig);
            SMTPServer server = createServer();
            server.setDnsService(dns);
            server.setProtocolHandlerLoader(loader);
            server.setFileSystem(fileSystem);
            server.setEncryptionFactory(encryptionFactory);
            server.setSaslMechanisms(saslMechanismLoader.load(serverConfig));
            server.setSaslAuthenticator(saslAuthenticator);
            server.configure(serverConfig);
            servers.add(server);
        }
        return servers.build();
    }

    /**
     * {@code handlerchain} may be omitted - the command set is ours - but must not swap the command set.
     * Extra handlers declared in it are kept, as an extension point (connection checks, rate limits...).
     */
    private static void pinCommandSet(HierarchicalConfiguration<ImmutableNode> serverConfig) throws ConfigurationException {
        String coreHandlersPackage = serverConfig.getString(CORE_HANDLERS_PACKAGE, null);
        if (coreHandlersPackage == null) {
            serverConfig.addProperty(CORE_HANDLERS_PACKAGE, SubmissionProxyHandlersLoader.class.getName());
        } else if (!coreHandlersPackage.equals(SubmissionProxyHandlersLoader.class.getName())) {
            throw new ConfigurationException("The submission proxy command set cannot be replaced, remove the "
                + "coreHandlersPackage attribute from " + CONFIGURATION_NAME + ".xml (found " + coreHandlersPackage + ")");
        }
    }
}
