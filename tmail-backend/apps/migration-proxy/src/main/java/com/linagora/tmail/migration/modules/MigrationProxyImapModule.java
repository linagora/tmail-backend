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

package com.linagora.tmail.migration.modules;

import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.imap.ImapSuite;
import org.apache.james.imap.api.ConnectionCheckFactory;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imapserver.netty.IMAPServerFactory;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.protocols.sasl.PlainSaslMechanismFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.migration.core.BackendRelay;
import com.linagora.tmail.migration.core.BackendResolver;
import com.linagora.tmail.migration.core.BackendSslContextFactory;
import com.linagora.tmail.migration.core.MigrationProxyConfiguration;
import com.linagora.tmail.migration.core.ProxyConnectionRegistry;
import com.linagora.tmail.migration.imap.ProxyImapProcessor;

/**
 * Starts the IMAP byte-proxy inside the same {@link org.apache.james.GuiceJamesServer}: an
 * {@link IMAPServerFactory} fed with our {@link ProxyImapProcessor}, configured from
 * {@code imapserver.xml} and started through an {@link InitializationOperation} (mirroring James'
 * {@code IMAPServerModule}).
 */
public class MigrationProxyImapModule extends AbstractModule {

    @Provides
    @Singleton
    MigrationProxyConfiguration provideMigrationProxyConfiguration(PropertiesProvider propertiesProvider) throws Exception {
        return MigrationProxyConfiguration.from(propertiesProvider.getConfiguration("migrationproxy"));
    }

    /**
     * Builds the processor of one {@code imapserver.xml} listener. Each listener gets its own, as its
     * {@code PLAIN} transport policy is its own: {@code auth.requireSSL}, else {@code plainAuthDisallowed},
     * defaulting to requiring an encrypted connection, as James does.
     */
    public interface ProxyImapProcessorFactory {
        ProxyImapProcessor forListener(HierarchicalConfiguration<ImmutableNode> listenerConfiguration);
    }

    @Provides
    @Singleton
    ProxyImapProcessorFactory provideProxyImapProcessorFactory(BackendResolver backendResolver, BackendRelay backendRelay,
                                                               BackendSslContextFactory sslContextFactory,
                                                               ProxyConnectionRegistry connectionRegistry,
                                                               MigrationProxyConfiguration configuration) throws ConfigurationException {
        Optional<SaslMechanism> gssapi = gssapiMechanism(configuration);
        PlainSaslMechanismFactory plainFactory = new PlainSaslMechanismFactory();
        return listenerConfiguration -> new ProxyImapProcessor(backendResolver, backendRelay, sslContextFactory,
            connectionRegistry, configuration.handshakeTimeout(), gssapi, plainFactory.create(listenerConfiguration));
    }

    /**
     * Building the mechanism validates the principal against the keytab and acquires the acceptor
     * credentials: a misconfigured Kerberos setup keeps the proxy from starting at all.
     */
    private static Optional<SaslMechanism> gssapiMechanism(MigrationProxyConfiguration configuration) throws ConfigurationException {
        if (configuration.kerberos().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(configuration.kerberos().get().saslMechanism());
    }

    @Provides
    @Singleton
    IMAPServerFactory provideImapServerFactory(FileSystem fileSystem, MetricFactory metricFactory,
                                               GaugeRegistry gaugeRegistry, ProxyImapProcessorFactory processorFactory) {
        ConnectionCheckFactory connectionCheckFactory = config -> ImmutableSet.of();
        ImapDecoder decoder = new DefaultImapDecoderFactory().buildImapDecoder();
        ImapEncoder encoder = new DefaultImapEncoderFactory().buildImapEncoder();
        return new IMAPServerFactory(fileSystem,
            listenerConfiguration -> new ImapSuite(decoder, encoder, processorFactory.forListener(listenerConfiguration)),
            metricFactory, gaugeRegistry, connectionCheckFactory);
    }

    @ProvidesIntoSet
    InitializationOperation configureImap(ConfigurationProvider configurationProvider, IMAPServerFactory imapServerFactory,
                                          Encryption.Factory encryptionFactory) {
        return InitilizationOperationBuilder
            .forClass(IMAPServerFactory.class)
            .init(() -> {
                imapServerFactory.setEncryptionFactory(encryptionFactory);
                imapServerFactory.configure(configurationProvider.getConfiguration("imapserver"));
                imapServerFactory.init();
            });
    }
}
