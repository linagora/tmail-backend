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

package com.linagora.tmail.james.jmap.settings;

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.Closeable;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.Username;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.model.User;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Connection;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

public class TWPSettingsConsumer implements Closeable, Startable, SimpleConnectionPool.ReconnectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TWPSettingsConsumer.class);
    private static final boolean REQUEUE_ON_NACK = true;
    private static final JmapSettingsKey LANGUAGE = JmapSettingsKey.liftOrThrow("language");
    private static final String TWP_SETTINGS_QUEUE = "tmail-settings";
    private static final String TWP_SETTINGS_DEAD_LETTER_QUEUE = "tmail-settings-dead-letter";

    private final ReceiverProvider receiverProvider;
    private final UsersRepository usersRepository;
    private final JmapSettingsRepository jmapSettingsRepository;
    private final Sender sender;
    private final RabbitMQConfiguration twpRabbitMQConfiguration;
    private final TWPCommonSettingsConfiguration twpCommonSettingsConfiguration;
    private Disposable consumeSettingsDisposable;

    @Inject
    public TWPSettingsConsumer(@Named(TWPSettingsModule.TWP_SETTINGS_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                               @Named(TWPSettingsModule.TWP_SETTINGS_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration,
                               UsersRepository usersRepository,
                               JmapSettingsRepository jmapSettingsRepository,
                               TWPCommonSettingsConfiguration twpCommonSettingsConfiguration) {
        this.receiverProvider = channelPool::createReceiver;
        this.sender = channelPool.getSender();
        this.usersRepository = usersRepository;
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.twpRabbitMQConfiguration = twpRabbitMQConfiguration;
        this.twpCommonSettingsConfiguration = twpCommonSettingsConfiguration;
    }

    public void init() {
        declareExchangeAndQueue(twpCommonSettingsConfiguration.exchange(), TWP_SETTINGS_QUEUE, TWP_SETTINGS_DEAD_LETTER_QUEUE);
        startConsumer();
    }

    public void declareExchangeAndQueue(String exchange, String queue, String deadLetter) {
        Flux.concat(
                sender.declareExchange(ExchangeSpecification.exchange(exchange)
                    .durable(DURABLE).type(BuiltinExchangeType.FANOUT.getType())),
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
                        .build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(exchange)
                    .queue(queue)
                    .routingKey(twpCommonSettingsConfiguration.routingKey())))
            .then()
            .block();
    }

    private QueueArguments.Builder queueArgumentSupplier() {
        if (!twpCommonSettingsConfiguration.quorumQueuesBypass()) {
            return twpRabbitMQConfiguration.workQueueArgumentsBuilder();
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
        return delivery(TWP_SETTINGS_QUEUE)
            .flatMap(delivery -> consumeSettingsUpdate(delivery, delivery.getBody()), DEFAULT_CONCURRENCY)
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
        return Mono.fromCallable(() -> usersRepository.getUserByName(Username.of(message.nickname())))
            .map(User::getUserName)
            .flatMap(username -> Mono.justOrEmpty(message.payload().language())
                .flatMap(language -> Mono.from(jmapSettingsRepository.updatePartial(username,
                    JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE, language))))
                .doOnNext(updatedLanguage -> LOGGER.info("Updated language setting for user {} to {}", username.asString(), updatedLanguage)))
            .then();
    }

    @Override
    public void close() {
        Optional.ofNullable(consumeSettingsDisposable).ifPresent(Disposable::dispose);
    }

    @Override
    public Publisher<Void> handleReconnection(Connection connection) {
        return Mono.fromRunnable(this::restartConsumer)
            .doOnError(error -> LOGGER.error("Error while handle reconnection for TWP settings consumer", error))
            .then();
    }
}
