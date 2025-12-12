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

package com.linagora.tmail.saas.rabbitmq.settings;

import static com.linagora.tmail.saas.rabbitmq.TWPConstants.TWP_INJECTION_KEY;

import java.io.FileNotFoundException;
import java.net.URI;
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
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.Host;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;

public class TWPSettingsRabbitmqModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding()
            .to(TWPSettingsDeadLetterQueueHealthCheck.class);
        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding()
            .to(TWPSettingsQueueConsumerHealthCheck.class);
    }

    @Provides
    @Singleton
    @Named(TWP_INJECTION_KEY)
    TWPSettingsUpdater provideTWPSettingsUpdater(UsersRepository usersRepository,
                                                 JmapSettingsRepository jmapSettingsRepository) {
        return new TWPSettingsUpdaterImpl(usersRepository, jmapSettingsRepository);
    }

    @Provides
    @Singleton
    TWPSettingsConsumer provideTWPSettingsConsumer(@Named(TWP_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                                   @Named(TWP_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration,
                                                   @Named(TWP_INJECTION_KEY) TWPSettingsUpdater twpSettingsUpdater,
                                                   TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                                                   TWPSettingsRabbitMQConfiguration twpSettingsRabbitMQConfiguration) {
        return new TWPSettingsConsumer(channelPool, rabbitMQConfiguration, twpCommonRabbitMQConfiguration,
            twpSettingsRabbitMQConfiguration, TWPSettingsConsumer.SettingsConsumerConfig.DEFAULT, twpSettingsUpdater);
    }

    @Provides
    @Singleton
    TWPSettingsQueueConsumerHealthCheck provideTWPSettingsQueueConsumerHealthCheck(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration,
                                                                                   TWPSettingsConsumer twpSettingsConsumer) {
        return new TWPSettingsQueueConsumerHealthCheck(twpRabbitMQConfiguration, twpSettingsConsumer, TWPSettingsConsumer.SettingsConsumerConfig.DEFAULT.queue());
    }

    @Provides
    @Singleton
    TWPSettingsDeadLetterQueueHealthCheck provideTWPSettingsDeadLetterQueueHealthCheck(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration) {
        return new TWPSettingsDeadLetterQueueHealthCheck(twpRabbitMQConfiguration, TWPSettingsConsumer.SettingsConsumerConfig.DEFAULT.deadLetterQueue());
    }

    @ProvidesIntoSet
    public InitializationOperation initializeTWPSettingsConsumer(TWPSettingsConsumer instance) {
        return InitilizationOperationBuilder
            .forClass(TWPSettingsConsumer.class)
            .init(instance::init);
    }

    @Provides
    @Singleton
    TWPCommonRabbitMQConfiguration provideTwpCommonRabbitMQConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return TWPCommonRabbitMQConfiguration.from(propertiesProvider.getConfiguration("rabbitmq"));
    }

    @Provides
    @Singleton
    TWPSettingsRabbitMQConfiguration provideTwpSettingsConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return TWPSettingsRabbitMQConfiguration.from(propertiesProvider.getConfiguration("rabbitmq"));
    }

    @Provides
    @Named(TWP_INJECTION_KEY)
    @Singleton
    public SimpleConnectionPool provideSimpleConnectionPool(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration) {
        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(rabbitMQConfiguration);
        return new SimpleConnectionPool(rabbitMQConnectionFactory, SimpleConnectionPool.Configuration.DEFAULT);
    }

    @Provides
    @Named(TWP_INJECTION_KEY)
    @Singleton
    public ReactorRabbitMQChannelPool provideReactorRabbitMQChannelPool(@Named(TWP_INJECTION_KEY) SimpleConnectionPool simpleConnectionPool,
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
    @Named(TWP_INJECTION_KEY)
    @Singleton
    public RabbitMQConfiguration provideTwpSettingsRabbitMQConfiguration(RabbitMQConfiguration commonRabbitMQConfiguration,
                                                                         TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration) {
        Optional<List<AmqpUri>> maybeTwpAmqpUris = twpCommonRabbitMQConfiguration.amqpUri();

        return maybeTwpAmqpUris.map(withTwpAmqpUris(commonRabbitMQConfiguration, twpCommonRabbitMQConfiguration.managementUri()))
            .orElse(commonRabbitMQConfiguration);
    }

    private Function<List<AmqpUri>, RabbitMQConfiguration> withTwpAmqpUris(RabbitMQConfiguration commonRabbitMQConfiguration,
                                                                           Optional<URI> managementUri) {
        return uris -> uris.getFirst()
            .toRabbitMqConfiguration(commonRabbitMQConfiguration, managementUri)
            .hosts(uris.stream().map(uri -> Host.from(uri.getUri().getHost(), uri.getPort())).collect(ImmutableList.toImmutableList()))
            .build();
    }
}
