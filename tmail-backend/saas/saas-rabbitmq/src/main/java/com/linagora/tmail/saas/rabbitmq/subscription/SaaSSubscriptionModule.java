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
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.SaaSDomainAccountRepository;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;

public class SaaSSubscriptionModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding()
            .to(SaaSSubscriptionDeadLetterQueueHealthCheck.class);
        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding()
            .to(SaaSSubscriptionQueueConsumerHealthCheck.class);
    }

    @Provides
    @Singleton
    SaaSSubscriptionConsumer provideSaaSSubscriptionConsumer(@Named(TWP_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                                             @Named(TWP_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration,
                                                             TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                                                             SaaSSubscriptionRabbitMQConfiguration saasSubscriptionRabbitMQConfiguration,
                                                             UsersRepository usersRepository,
                                                             SaaSAccountRepository saasAccountRepository,
                                                             MaxQuotaManager maxQuotaManager,
                                                             UserQuotaRootResolver userQuotaRootResolver,
                                                             RateLimitingRepository rateLimitingRepository) {
        return new SaaSSubscriptionConsumer(channelPool, rabbitMQConfiguration, twpCommonRabbitMQConfiguration,
            saasSubscriptionRabbitMQConfiguration,
            new SaaSSubscriptionHandlerImpl(usersRepository,
                saasAccountRepository,
                maxQuotaManager,
                userQuotaRootResolver,
                rateLimitingRepository));
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
                                                                         UsersRepository usersRepository,
                                                                         SaaSAccountRepository saaSAccountRepository,
                                                                         SaaSDomainAccountRepository saaSDomainAccountRepository) {
        return new SaaSDomainSubscriptionConsumer(channelPool, rabbitMQConfiguration, twpCommonRabbitMQConfiguration,
            saasSubscriptionRabbitMQConfiguration,
            new SaaSDomainSubscriptionHandlerImpl(domainList,
                maxQuotaManager,
                rateLimitingRepository,
                usersRepository,
                saaSAccountRepository,
                saaSDomainAccountRepository));
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
