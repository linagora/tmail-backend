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
 *******************************************************************/

package com.linagora.tmail.saas.rabbitmq.subscription;

import static com.linagora.tmail.saas.rabbitmq.TWPConstants.TWP_INJECTION_KEY;

import java.io.FileNotFoundException;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.rabbitmq.MonitoredDeadLetterQueue;
import org.apache.james.backends.rabbitmq.MonitoredRabbitMQConsumers;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSDomainSubscriptionConsumer.DomainSubscriptionConsumerConfig;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSSubscriptionConsumer.SubscriptionConsumerConfig;

import reactor.core.publisher.Mono;

public class SaaSSubscriptionModule extends AbstractModule {
    @Provides
    @Singleton
    SaaSSubscriptionConsumer provideSaaSSubscriptionConsumer(@Named(TWP_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                                             @Named(TWP_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration,
                                                             TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                                                             SaaSSubscriptionRabbitMQConfiguration saasSubscriptionRabbitMQConfiguration,
                                                             SaaSAccountRepository saasAccountRepository,
                                                             MaxQuotaManager maxQuotaManager,
                                                             UserQuotaRootResolver userQuotaRootResolver,
                                                             RateLimitingRepository rateLimitingRepository) {
        return new SaaSSubscriptionConsumer(channelPool, rabbitMQConfiguration, twpCommonRabbitMQConfiguration,
            saasSubscriptionRabbitMQConfiguration,
            new SaaSSubscriptionHandlerImpl(saasAccountRepository,
                maxQuotaManager,
                userQuotaRootResolver,
                rateLimitingRepository),
            SubscriptionConsumerConfig.DEFAULT);
    }

    @Provides
    @Singleton
    SaaSDomainSubscriptionConsumer provideSaaSDomainSubscriptionConsumer(@Named(TWP_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                                                         @Named(TWP_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration,
                                                                         TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                                                                         SaaSSubscriptionRabbitMQConfiguration saasSubscriptionRabbitMQConfiguration,
                                                                         DomainList domainList,
                                                                         MaxQuotaManager maxQuotaManager,
                                                                         RateLimitingRepository rateLimitingRepository,
                                                                         SaaSAccountRepository saasAccountRepository) {
        return new SaaSDomainSubscriptionConsumer(channelPool, rabbitMQConfiguration, twpCommonRabbitMQConfiguration,
            saasSubscriptionRabbitMQConfiguration,
            new SaaSDomainSubscriptionHandlerImpl(domainList,
                maxQuotaManager,
                rateLimitingRepository,
                saasAccountRepository),
            DomainSubscriptionConsumerConfig.DEFAULT);
    }

    @ProvidesIntoSet
    MonitoredRabbitMQConsumers saaSSubscriptionConsumers(@Named(TWP_INJECTION_KEY) SimpleConnectionPool twpConnectionPool,
                                                         SaaSSubscriptionConsumer saaSSubscriptionConsumer) {
        return MonitoredRabbitMQConsumers.of("SaaS subscription", twpConnectionPool,
            () -> ImmutableList.of(SubscriptionConsumerConfig.DEFAULT.queue()),
            connection -> Mono.fromRunnable(saaSSubscriptionConsumer::restartConsumer));
    }

    @ProvidesIntoSet
    MonitoredRabbitMQConsumers saaSDomainSubscriptionConsumers(@Named(TWP_INJECTION_KEY) SimpleConnectionPool twpConnectionPool,
                                                               SaaSDomainSubscriptionConsumer saaSDomainSubscriptionConsumer) {
        return MonitoredRabbitMQConsumers.of("SaaS domain subscription", twpConnectionPool,
            () -> ImmutableList.of(DomainSubscriptionConsumerConfig.DEFAULT.queue()),
            connection -> Mono.fromRunnable(saaSDomainSubscriptionConsumer::restartConsumer));
    }

    @ProvidesIntoSet
    MonitoredDeadLetterQueue saaSSubscriptionDeadLetterQueue(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration) {
        return new MonitoredDeadLetterQueue(twpRabbitMQConfiguration, SubscriptionConsumerConfig.DEFAULT.deadLetterQueue());
    }

    @ProvidesIntoSet
    MonitoredDeadLetterQueue saaSDomainSubscriptionDeadLetterQueue(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration) {
        return new MonitoredDeadLetterQueue(twpRabbitMQConfiguration, DomainSubscriptionConsumerConfig.DEFAULT.deadLetterQueue());
    }

    @ProvidesIntoSet
    public InitializationOperation initializeSaaSSubscriptionConsumer(SaaSSubscriptionConsumer instance) {
        return InitilizationOperationBuilder
            .forClass(SaaSSubscriptionConsumer.class)
            .init(instance::init);
    }

    @ProvidesIntoSet
    public InitializationOperation initializeSaaSDomainSubscriptionConsumer(SaaSDomainSubscriptionConsumer instance) {
        return InitilizationOperationBuilder
            .forClass(SaaSDomainSubscriptionConsumer.class)
            .init(instance::init);
    }

    @Provides
    @Singleton
    SaaSSubscriptionRabbitMQConfiguration provideSaasSubscriptionRabbitMQConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return SaaSSubscriptionRabbitMQConfiguration.from(propertiesProvider.getConfiguration("rabbitmq"));
    }
}
