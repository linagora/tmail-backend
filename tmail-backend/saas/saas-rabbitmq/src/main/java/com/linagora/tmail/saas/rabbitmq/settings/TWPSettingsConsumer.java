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

package com.linagora.tmail.saas.rabbitmq.settings;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.Closeable;
import java.time.Duration;
import java.util.Optional;

import jakarta.annotation.PreDestroy;

import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.lifecycle.api.Startable;

import com.linagora.tmail.rabbitmq.ManagedRabbitMQConsumer;
import com.linagora.tmail.rabbitmq.QueueDeclaration;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;
import com.rabbitmq.client.BuiltinExchangeType;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;

public class TWPSettingsConsumer implements Closeable, Startable {
    public record SettingsConsumerConfig(String queue, String deadLetterQueue) {
        public static SettingsConsumerConfig DEFAULT = new SettingsConsumerConfig("tmail-settings", "tmail-settings-dead-letter");
    }

    private static final Duration CONSUMER_TIMEOUT = Duration.ofMinutes(10L);

    private final ManagedRabbitMQConsumer consumer;
    private final TWPSettingsUpdater settingsUpdater;

    public TWPSettingsConsumer(ReactorRabbitMQChannelPool channelPool,
                               RabbitMQConfiguration rabbitMQConfiguration,
                               TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                               TWPSettingsRabbitMQConfiguration twpSettingsRabbitMQConfiguration,
                               SettingsConsumerConfig consumerConfig,
                               TWPSettingsUpdater settingsUpdater) {
        this.settingsUpdater = settingsUpdater;
        this.consumer = new ManagedRabbitMQConsumer.Factory(channelPool)
            .create(new ManagedRabbitMQConsumer.Parameters(
                QueueDeclaration.singleExchange(twpSettingsRabbitMQConfiguration.exchange(), BuiltinExchangeType.TOPIC, twpSettingsRabbitMQConfiguration.routingKey(),
                    consumerConfig.queue(),
                    consumerConfig.deadLetterQueue(), BuiltinExchangeType.FANOUT, consumerConfig.deadLetterQueue(),
                    Optional.empty()),
                () -> queueArgumentSupplier(rabbitMQConfiguration, twpCommonRabbitMQConfiguration),
                true,
                Optional.of(CONSUMER_TIMEOUT),
                Optional.of(DEFAULT_CONCURRENCY),
                1,
                this::consumeSettingsUpdate));
    }

    private static QueueArguments.Builder queueArgumentSupplier(RabbitMQConfiguration rabbitMQConfiguration,
                                                                TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration) {
        if (!twpCommonRabbitMQConfiguration.quorumQueuesBypass()) {
            return rabbitMQConfiguration.workQueueArgumentsBuilder();
        }
        return QueueArguments.builder();
    }

    public void init() {
        consumer.init();
    }

    public void restartConsumer() {
        consumer.restart();
    }

    private Mono<Void> consumeSettingsUpdate(AcknowledgableDelivery ackDelivery) {
        return Mono.fromCallable(() -> TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(ackDelivery.getBody()))
            .flatMap(settingsUpdater::updateSettings);
    }

    @PreDestroy
    @Override
    public void close() {
        consumer.close();
    }
}
