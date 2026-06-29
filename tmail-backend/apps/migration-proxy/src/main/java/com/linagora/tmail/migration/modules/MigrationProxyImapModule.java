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

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.imap.api.ConnectionCheckFactory;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imapserver.netty.IMAPServerFactory;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.netty.Encryption;
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

    @Provides
    @Singleton
    ConnectionCheckFactory provideConnectionCheckFactory() {
        return config -> ImmutableSet.of();
    }

    @Provides
    @Singleton
    IMAPServerFactory provideImapServerFactory(FileSystem fileSystem, MetricFactory metricFactory,
                                               GaugeRegistry gaugeRegistry, ConnectionCheckFactory connectionCheckFactory,
                                               Encryption.Factory encryptionFactory,
                                               BackendResolver backendResolver, BackendRelay backendRelay,
                                               BackendSslContextFactory sslContextFactory) {
        ProxyImapProcessor processor = new ProxyImapProcessor(backendResolver, backendRelay, sslContextFactory);
        IMAPServerFactory factory = new IMAPServerFactory(fileSystem,
            new DefaultImapDecoderFactory().buildImapDecoder(),
            new DefaultImapEncoderFactory().buildImapEncoder(),
            processor, metricFactory, gaugeRegistry, connectionCheckFactory);
        factory.setEncryptionFactory(encryptionFactory);
        return factory;
    }

    @ProvidesIntoSet
    InitializationOperation configureImap(ConfigurationProvider configurationProvider, IMAPServerFactory imapServerFactory) {
        return InitilizationOperationBuilder
            .forClass(IMAPServerFactory.class)
            .init(() -> {
                imapServerFactory.configure(configurationProvider.getConfiguration("imapserver"));
                imapServerFactory.init();
            });
    }
}
