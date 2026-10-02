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
import org.apache.james.backends.rabbitmq.MonitoredDeadLetterQueue;
import org.apache.james.backends.rabbitmq.MonitoredRabbitMQConsumers;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.webadmin.service.DeleteUserDataService;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.rabbitmq.ManagedRabbitMQConsumer;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;

import reactor.core.publisher.Mono;

public class TWPUserDeletionRabbitmqModule extends AbstractModule {
    private static final String TWP_USER_DELETION_CONSUMER = "twp-user-deletion";

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

    @ProvidesIntoSet
    MonitoredRabbitMQConsumers twpUserDeletionConsumers(@Named(TWP_INJECTION_KEY) SimpleConnectionPool twpConnectionPool,
                                                        @Named(TWP_USER_DELETION_CONSUMER) ManagedRabbitMQConsumer twpUserDeletionConsumer) {
        return MonitoredRabbitMQConsumers.of("TWP user deletion", twpConnectionPool,
            () -> ImmutableList.of(TWPUserDeletionConsumer.UserDeletionConsumerConfig.DEFAULT.queue()),
            connection -> Mono.fromRunnable(twpUserDeletionConsumer::restart));
    }

    @ProvidesIntoSet
    MonitoredDeadLetterQueue twpUserDeletionDeadLetterQueue(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration) {
        return new MonitoredDeadLetterQueue(twpRabbitMQConfiguration, TWPUserDeletionConsumer.UserDeletionConsumerConfig.DEFAULT.deadLetterQueue());
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
