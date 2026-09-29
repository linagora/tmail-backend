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

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.migration.core.BackendRelay;
import com.linagora.tmail.migration.core.BackendResolver;
import com.linagora.tmail.migration.core.BackendSslContextFactory;
import com.linagora.tmail.migration.core.MigrationProxyMetrics;
import com.linagora.tmail.migration.core.ProxyConnectionRegistry;
import com.linagora.tmail.migration.submission.SubmissionBackendHandover;
import com.linagora.tmail.migration.submission.SubmissionProxyConfiguration;
import com.linagora.tmail.migration.submission.SubmissionProxyServerFactory;

/**
 * Opt-in SMTP submission proxy ({@code submission.enabled=true} in {@code migrationproxy.properties}):
 * starts the listeners of {@code submissionproxy.xml}, which authenticate each client against the
 * submission port of its backend and then relay the connection raw, like the IMAP proxy does.
 *
 * <p>The factory is constructor-injected, rather than provided, so that the Guice lifecycle stops its
 * servers along with the proxy.
 */
public class SubmissionProxyModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SubmissionProxyServerFactory.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    SubmissionProxyConfiguration provideSubmissionProxyConfiguration(PropertiesProvider propertiesProvider) throws Exception {
        return SubmissionProxyConfiguration.from(propertiesProvider.getConfiguration("migrationproxy"));
    }

    @Provides
    @Singleton
    SubmissionBackendHandover provideSubmissionBackendHandover(BackendResolver backendResolver,
                                                               BackendSslContextFactory sslContextFactory,
                                                               ProxyConnectionRegistry connectionRegistry,
                                                               SubmissionProxyConfiguration configuration,
                                                               MetricFactory metricFactory) {
        // Relay volumes are accounted apart from the IMAP ones although both target the old/new backends.
        BackendRelay relay = new BackendRelay(new MigrationProxyMetrics(metricFactory, MigrationProxyMetrics.SUBMISSION));
        return new SubmissionBackendHandover(backendResolver, relay, sslContextFactory, connectionRegistry, configuration);
    }

    @ProvidesIntoSet
    InitializationOperation configureSubmissionProxy(ConfigurationProvider configurationProvider,
                                                     SubmissionProxyServerFactory serverFactory) {
        return InitilizationOperationBuilder
            .forClass(SubmissionProxyServerFactory.class)
            .init(() -> {
                serverFactory.configure(configurationProvider.getConfiguration(SubmissionProxyServerFactory.CONFIGURATION_NAME));
                serverFactory.init();
            });
    }
}
