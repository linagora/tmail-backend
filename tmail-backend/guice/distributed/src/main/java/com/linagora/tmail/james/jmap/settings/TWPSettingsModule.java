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

package com.linagora.tmail.james.jmap.settings;

import static com.linagora.tmail.james.jmap.settings.TWPSettingsConsumer.TWP_SETTINGS_INJECTION_KEY;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.Host;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.AmqpUri;

public class TWPSettingsModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(TWPSettingsModule.class);

    @Override
    protected void configure() {
        bind(TWPReadOnlyPropertyProvider.class).in(Scopes.SINGLETON);
        bind(TWPSettingsConsumer.class).in(Scopes.SINGLETON);
    }

    @ProvidesIntoSet
    public InitializationOperation initializeTWPSettingsConsumer(TWPSettingsConsumer instance) {
        return InitilizationOperationBuilder
            .forClass(TWPSettingsConsumer.class)
            .init(instance::init);
    }

    @Provides
    @Singleton
    TWPCommonSettingsConfiguration provideCommonSettingsConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return TWPCommonSettingsConfiguration.from(propertiesProvider.getConfiguration("jmap"),
            propertiesProvider.getConfiguration("rabbitmq"));
    }

    @Provides
    @Named(TWP_SETTINGS_INJECTION_KEY)
    @Singleton
    public SimpleConnectionPool provideSimpleConnectionPool(@Named(TWP_SETTINGS_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration) {
        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(rabbitMQConfiguration);
        try {
            return new SimpleConnectionPool(rabbitMQConnectionFactory, SimpleConnectionPool.Configuration.DEFAULT);
        } catch (Exception e) {
            LOGGER.info("Error while retrieving SimpleConnectionPool.Configuration, falling back to defaults.", e);
            return new SimpleConnectionPool(rabbitMQConnectionFactory, SimpleConnectionPool.Configuration.DEFAULT);
        }
    }

    @Provides
    @Named(TWP_SETTINGS_INJECTION_KEY)
    @Singleton
    public ReactorRabbitMQChannelPool provideReactorRabbitMQChannelPool(@Named(TWP_SETTINGS_INJECTION_KEY) SimpleConnectionPool simpleConnectionPool,
                                                                        MetricFactory metricFactory,
                                                                        GaugeRegistry gaugeRegistry) {

        ReactorRabbitMQChannelPool channelPool = new ReactorRabbitMQChannelPool(
            simpleConnectionPool.getResilientConnection(),
            ReactorRabbitMQChannelPool.Configuration.DEFAULT,
            metricFactory,
            gaugeRegistry);
        channelPool.start();
        return channelPool;
    }

    @Provides
    @Named(TWP_SETTINGS_INJECTION_KEY)
    @Singleton
    public RabbitMQConfiguration provideTwpSettingsRabbitMQConfiguration(RabbitMQConfiguration commonRabbitMQConfiguration,
                                                                         TWPCommonSettingsConfiguration twpCommonSettingsConfiguration) {
        Optional<List<AmqpUri>> maybeTwpAmqpUris = twpCommonSettingsConfiguration.amqpUri();

        return maybeTwpAmqpUris.map(withTwpAmqpUris(commonRabbitMQConfiguration))
            .orElse(commonRabbitMQConfiguration);
    }

    private Function<List<AmqpUri>, RabbitMQConfiguration> withTwpAmqpUris(RabbitMQConfiguration commonRabbitMQConfiguration) {
        return uris -> uris.getFirst()
            .toRabbitMqConfiguration(commonRabbitMQConfiguration)
            .hosts(uris.stream().map(uri -> Host.from(uri.getUri().getHost(), uri.getPort())).collect(ImmutableList.toImmutableList()))
            .build();
    }
}
