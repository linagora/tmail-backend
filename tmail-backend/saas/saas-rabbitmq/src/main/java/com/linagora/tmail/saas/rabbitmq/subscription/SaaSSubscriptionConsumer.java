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
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.Closeable;
import java.time.Duration;
import java.util.Optional;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;

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

public class SaaSSubscriptionConsumer implements Closeable, Startable {
    public record SubscriptionConsumerConfig(String queue, String deadLetterQueue) {
        public static SubscriptionConsumerConfig DEFAULT = new SubscriptionConsumerConfig("tmail-saas-subscription", "tmail-saas-subscription-dead-letter");
    }

    private static final Duration CONSUMER_TIMEOUT = Duration.ofMinutes(10L);

    private final ManagedRabbitMQConsumer consumer;
    private final SaaSMessageHandler saasSubscriptionHandler;
    private final SubscriptionConsumerConfig consumerConfig;

    public SaaSSubscriptionConsumer(@Named(TWP_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                    @Named(TWP_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration,
                                    TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                                    SaaSSubscriptionRabbitMQConfiguration saasSubscriptionRabbitMQConfiguration, SaaSMessageHandler saasSubscriptionHandler,
                                    SubscriptionConsumerConfig consumerConfig) {
        this.saasSubscriptionHandler = saasSubscriptionHandler;
        this.consumerConfig = consumerConfig;
        this.consumer = new ManagedRabbitMQConsumer.Factory(channelPool)
            .create(new ManagedRabbitMQConsumer.Parameters(
                QueueDeclaration.singleExchange(saasSubscriptionRabbitMQConfiguration.exchange(), BuiltinExchangeType.TOPIC, saasSubscriptionRabbitMQConfiguration.routingKey(),
                    consumerConfig.queue(),
                    consumerConfig.deadLetterQueue(), BuiltinExchangeType.FANOUT, consumerConfig.deadLetterQueue(),
                    Optional.empty()),
                () -> queueArgumentSupplier(rabbitMQConfiguration, twpCommonRabbitMQConfiguration),
                true,
                Optional.of(CONSUMER_TIMEOUT),
                Optional.of(DEFAULT_CONCURRENCY),
                1,
                this::consumeSubscriptionUpdate));
    }

    static QueueArguments.Builder queueArgumentSupplier(RabbitMQConfiguration rabbitMQConfiguration,
                                                        TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration) {
        if (!twpCommonRabbitMQConfiguration.quorumQueuesBypass()) {
            return rabbitMQConfiguration.workQueueArgumentsBuilder();
        }
        return QueueArguments.builder();
    }

    public void init() {
        consumer.init();
    }

    protected SubscriptionConsumerConfig getConsumerConfig() {
        return consumerConfig;
    }

    public void restartConsumer() {
        consumer.restart();
    }

    private Mono<Void> consumeSubscriptionUpdate(AcknowledgableDelivery ackDelivery) {
        return saasSubscriptionHandler.handleMessage(ackDelivery.getBody());
    }

    @PreDestroy
    @Override
    public void close() {
        consumer.close();
    }
}
