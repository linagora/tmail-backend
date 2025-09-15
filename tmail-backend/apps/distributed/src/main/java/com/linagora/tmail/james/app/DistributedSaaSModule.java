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

package com.linagora.tmail.james.app;

import static com.linagora.tmail.modules.data.TMailCassandraDelegationStoreModule.TMAIL_CASSANDRA_USER;

import java.io.FileNotFoundException;
import java.util.function.Function;

import jakarta.inject.Named;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;

import com.datastax.oss.driver.api.querybuilder.schema.CreateTable;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTableStart;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.james.jmap.saas.SaaSCapabilitiesModule;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.SaaSAccountUsernameChangeTaskStep;
import com.linagora.tmail.saas.api.cassandra.CassandraSaaSAccountRepository;
import com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSSubscriptionConsumer;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSSubscriptionDeadLetterQueueHealthCheck;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSSubscriptionQueueConsumerHealthCheck;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSSubscriptionRabbitMQConfiguration;

public class DistributedSaaSModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new SaaSCapabilitiesModule());

        bind(SaaSAccountRepository.class).to(CassandraSaaSAccountRepository.class)
            .in(Scopes.SINGLETON);
        bind(SaaSSubscriptionConsumer.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding()
            .to(SaaSSubscriptionDeadLetterQueueHealthCheck.class);
        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding()
            .to(SaaSSubscriptionQueueConsumerHealthCheck.class);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class)
            .addBinding()
            .to(SaaSAccountUsernameChangeTaskStep.class);
    }

    @Provides
    @Singleton
    @Named(TMAIL_CASSANDRA_USER)
    public Function<CreateTableStart, CreateTable> overrideCreateUserTableFunction() {
        return CassandraSaaSDataDefinition.userTableWithSaaSSupport();
    }

    @ProvidesIntoSet
    public InitializationOperation initializeSaaSSubscriptionConsumer(SaaSSubscriptionConsumer instance) {
        return InitilizationOperationBuilder
            .forClass(SaaSSubscriptionConsumer.class)
            .init(instance::init);
    }

    @Provides
    @Singleton
    SaaSSubscriptionRabbitMQConfiguration provideSaasSubscriptionRabbitMQConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return SaaSSubscriptionRabbitMQConfiguration.from(propertiesProvider.getConfiguration("rabbitmq"));
    }
}
