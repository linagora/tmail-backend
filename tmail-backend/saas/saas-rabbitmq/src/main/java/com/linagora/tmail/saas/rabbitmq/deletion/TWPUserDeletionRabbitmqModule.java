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

package com.linagora.tmail.saas.rabbitmq.deletion;

import static com.linagora.tmail.saas.rabbitmq.TWPConstants.TWP_INJECTION_KEY;

import java.io.FileNotFoundException;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.webadmin.service.DeleteUserDataService;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.rabbitmq.ManagedRabbitMQConsumer;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;

public class TWPUserDeletionRabbitmqModule extends AbstractModule {
    private static final String TWP_USER_DELETION_CONSUMER = "twp-user-deletion";

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding()
            .to(TWPUserDeletionDeadLetterQueueHealthCheck.class);
        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding()
            .to(TWPUserDeletionQueueConsumerHealthCheck.class);
    }

    @Provides
    @Singleton
    @Named(TWP_USER_DELETION_CONSUMER)
    ManagedRabbitMQConsumer provideTWPUserDeletionConsumer(@Named(TWP_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                                           @Named(TWP_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration,
                                                           TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                                                           TWPUserDeletionRabbitMQConfiguration twpUserDeletionRabbitMQConfiguration,
                                                           DeleteUserDataService deleteUserDataService) {
        return TWPUserDeletionConsumer.create(channelPool, rabbitMQConfiguration, twpCommonRabbitMQConfiguration,
            twpUserDeletionRabbitMQConfiguration, TWPUserDeletionConsumer.UserDeletionConsumerConfig.DEFAULT, deleteUserDataService);
    }

    @Provides
    @Singleton
    TWPUserDeletionQueueConsumerHealthCheck provideTWPUserDeletionQueueConsumerHealthCheck(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration,
                                                                                           @Named(TWP_USER_DELETION_CONSUMER) ManagedRabbitMQConsumer twpUserDeletionConsumer) {
        return new TWPUserDeletionQueueConsumerHealthCheck(twpRabbitMQConfiguration, twpUserDeletionConsumer, TWPUserDeletionConsumer.UserDeletionConsumerConfig.DEFAULT.queue());
    }

    @Provides
    @Singleton
    TWPUserDeletionDeadLetterQueueHealthCheck provideTWPUserDeletionDeadLetterQueueHealthCheck(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration) {
        return new TWPUserDeletionDeadLetterQueueHealthCheck(twpRabbitMQConfiguration, TWPUserDeletionConsumer.UserDeletionConsumerConfig.DEFAULT.deadLetterQueue());
    }

    @ProvidesIntoSet
    public InitializationOperation initializeTWPUserDeletionConsumer(@Named(TWP_USER_DELETION_CONSUMER) ManagedRabbitMQConsumer instance) {
        return InitilizationOperationBuilder
            .forClass(ManagedRabbitMQConsumer.class)
            .init(instance::init);
    }

    @Provides
    @Singleton
    TWPUserDeletionRabbitMQConfiguration provideTWPUserDeletionConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return TWPUserDeletionRabbitMQConfiguration.from(propertiesProvider.getConfiguration("rabbitmq"));
    }
}
