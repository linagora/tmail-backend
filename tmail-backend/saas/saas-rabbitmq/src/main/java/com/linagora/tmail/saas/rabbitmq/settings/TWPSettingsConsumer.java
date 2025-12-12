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

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.Closeable;
import java.time.Duration;
import java.util.Optional;

import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.ShutdownSignalException;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

public class TWPSettingsConsumer implements Closeable, Startable {
    public record SettingsConsumerConfig(String queue, String deadLetterQueue) {
        public static SettingsConsumerConfig DEFAULT = new SettingsConsumerConfig("tmail-settings", "tmail-settings-dead-letter");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TWPSettingsConsumer.class);
    private static final boolean REQUEUE_ON_NACK = true;

    private final ReceiverProvider receiverProvider;
    private final Sender sender;
    private final RabbitMQConfiguration rabbitMQConfiguration;
    private final TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration;
    private final TWPSettingsRabbitMQConfiguration twpSettingsRabbitMQConfiguration;
    private Disposable consumeSettingsDisposable;
    private final SettingsConsumerConfig consumerConfig;
    private final TWPSettingsUpdater settingsUpdater;

    public TWPSettingsConsumer(ReactorRabbitMQChannelPool channelPool,
                               RabbitMQConfiguration rabbitMQConfiguration,
                               TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                               TWPSettingsRabbitMQConfiguration twpSettingsRabbitMQConfiguration,
                               SettingsConsumerConfig consumerConfig,
                               TWPSettingsUpdater settingsUpdater) {
        this.receiverProvider = channelPool::createReceiver;
        this.sender = channelPool.getSender();
        this.rabbitMQConfiguration = rabbitMQConfiguration;
        this.twpCommonRabbitMQConfiguration = twpCommonRabbitMQConfiguration;
        this.twpSettingsRabbitMQConfiguration = twpSettingsRabbitMQConfiguration;
        this.consumerConfig = consumerConfig;
        this.settingsUpdater = settingsUpdater;
    }

    public void init() {
        declareExchangeAndQueue(twpSettingsRabbitMQConfiguration.exchange(), consumerConfig.queue(), consumerConfig.deadLetterQueue());
        startConsumer();
    }

    public void declareExchangeAndQueue(String exchange, String queue, String deadLetter) {
        Flux.concat(
                declareExchange(exchange),
                sender.declareQueue(QueueSpecification
                    .queue(deadLetter)
                    .durable(DURABLE)
                    .arguments(queueArgumentSupplier()
                        .build())),
                sender.declareQueue(QueueSpecification
                    .queue(queue)
                    .durable(DURABLE)
                    .arguments(queueArgumentSupplier()
                        .deadLetter(deadLetter)
                        .singleActiveConsumer()
                        .consumerTimeout(Duration.ofMinutes(10L).toMillis())
                        .build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(exchange)
                    .queue(queue)
                    .routingKey(twpSettingsRabbitMQConfiguration.routingKey())))
            .then()
            .block();
    }

    private Mono<AMQP.Exchange.DeclareOk> declareExchange(String exchange) {
        return sender.declareExchange(ExchangeSpecification.exchange(exchange)
                .durable(DURABLE).type(BuiltinExchangeType.TOPIC.getType()))
            .onErrorResume(error -> error instanceof ShutdownSignalException && error.getMessage().contains("reply-code=406, reply-text=PRECONDITION_FAILED"),
                error -> {
                    LOGGER.warn("Exchange `{}` already exists but with different configuration. Ignoring this error. \nError message: {}", exchange, error.getMessage());
                    return Mono.empty();
                });
    }

    private QueueArguments.Builder queueArgumentSupplier() {
        if (!twpCommonRabbitMQConfiguration.quorumQueuesBypass()) {
            return rabbitMQConfiguration.workQueueArgumentsBuilder();
        }
        return QueueArguments.builder();
    }

    public void startConsumer() {
        consumeSettingsDisposable = consumeSettingsQueue();
    }

    public void restartConsumer() {
        Disposable previousConsumer = consumeSettingsDisposable;
        consumeSettingsDisposable = consumeSettingsQueue();
        Optional.ofNullable(previousConsumer)
            .ifPresent(Disposable::dispose);
    }

    private Disposable consumeSettingsQueue() {
        return delivery(consumerConfig.queue())
            .concatMap(delivery -> consumeSettingsUpdate(delivery, delivery.getBody()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    public Flux<AcknowledgableDelivery> delivery(String queue) {
        return Flux.using(receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(queue, new ConsumeOptions()
                .qos(DEFAULT_CONCURRENCY)),
            Receiver::close);
    }

    private Mono<Void> consumeSettingsUpdate(AcknowledgableDelivery ackDelivery, byte[] messagePayload) {
        return Mono.fromCallable(() -> TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(messagePayload))
            .flatMap(this::handleSettingsMessage)
            .doOnSuccess(result -> {
                LOGGER.debug("Consumed TWP settings message successfully");
                ackDelivery.ack();
            })
            .onErrorResume(error -> {
                LOGGER.error("Error when consuming TWP settings message", error);
                ackDelivery.nack(!REQUEUE_ON_NACK);
                return Mono.empty();
            });
    }

    private Mono<Void> handleSettingsMessage(TWPCommonSettingsMessage message) {
        return settingsUpdater.updateSettings(message);
    }

    @Override
    public void close() {
        Optional.ofNullable(consumeSettingsDisposable).ifPresent(Disposable::dispose);
    }
}
