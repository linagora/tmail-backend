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

package com.linagora.tmail.rabbitmq;

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;

import java.io.Closeable;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;

import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
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

public class ManagedRabbitMQConsumer implements Startable, Closeable {

    public record Parameters(QueueDeclaration queueDeclaration,
                             Supplier<QueueArguments.Builder> queueArguments,
                             boolean singleActiveConsumer,
                             Optional<Duration> consumerTimeout,
                             Optional<Integer> qos,
                             int concurrency,
                             Function<AcknowledgableDelivery, Mono<Void>> handleDelivery) {

        public static final int SEQUENTIAL = 1;

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private QueueDeclaration queueDeclaration;
            private Supplier<QueueArguments.Builder> queueArguments = QueueArguments::builder;
            private boolean singleActiveConsumer = false;
            private Optional<Duration> consumerTimeout = Optional.empty();
            private Optional<Integer> qos = Optional.empty();
            private int concurrency = SEQUENTIAL;
            private Function<AcknowledgableDelivery, Mono<Void>> handleDelivery;

            public Builder queueDeclaration(QueueDeclaration queueDeclaration) {
                this.queueDeclaration = queueDeclaration;
                return this;
            }

            public Builder queueArguments(Supplier<QueueArguments.Builder> queueArguments) {
                this.queueArguments = queueArguments;
                return this;
            }

            public Builder singleActiveConsumer() {
                this.singleActiveConsumer = true;
                return this;
            }

            public Builder consumerTimeout(Duration consumerTimeout) {
                this.consumerTimeout = Optional.of(consumerTimeout);
                return this;
            }

            public Builder qos(int qos) {
                this.qos = Optional.of(qos);
                return this;
            }

            public Builder concurrency(int concurrency) {
                this.concurrency = concurrency;
                return this;
            }

            public Builder handleDelivery(Function<AcknowledgableDelivery, Mono<Void>> handleDelivery) {
                this.handleDelivery = handleDelivery;
                return this;
            }

            public Parameters build() {
                Preconditions.checkState(queueDeclaration != null, "'queueDeclaration' is compulsory");
                Preconditions.checkState(handleDelivery != null, "'handleDelivery' is compulsory");

                return new Parameters(queueDeclaration, queueArguments, singleActiveConsumer,
                    consumerTimeout, qos, concurrency, handleDelivery);
            }
        }
    }

    public static class Factory {
        private final Sender sender;
        private final ReceiverProvider receiverProvider;

        public Factory(Sender sender, ReceiverProvider receiverProvider) {
            this.sender = sender;
            this.receiverProvider = receiverProvider;
        }

        public Factory(ReactorRabbitMQChannelPool channelPool) {
            this(channelPool.getSender(), channelPool::createReceiver);
        }

        public ManagedRabbitMQConsumer create(Parameters parameters) {
            return new ManagedRabbitMQConsumer(sender, receiverProvider, parameters);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedRabbitMQConsumer.class);
    private static final boolean REQUEUE_ON_NACK = false;

    private final Sender sender;
    private final ReceiverProvider receiverProvider;
    private final Parameters parameters;
    private Disposable consumer;

    private ManagedRabbitMQConsumer(Sender sender, ReceiverProvider receiverProvider, Parameters parameters) {
        this.sender = sender;
        this.receiverProvider = receiverProvider;
        this.parameters = parameters;
    }

    public void init() {
        declare().block();
        start();
    }

    public Mono<Void> declare() {
        QueueDeclaration spec = parameters.queueDeclaration();
        return Flux.concat(
                Flux.fromIterable(spec.bindings())
                    .concatMap(binding -> declareExchange(binding.exchange(), binding.exchangeType())),
                declareExchange(spec.deadLetterExchange(), spec.deadLetterExchangeType()),
                sender.declareQueue(QueueSpecification.queue(spec.deadLetterQueue())
                    .durable(DURABLE)
                    .arguments(parameters.queueArguments().get()
                        .build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(spec.deadLetterExchange())
                    .queue(spec.deadLetterQueue())
                    .routingKey(EMPTY_ROUTING_KEY)),
                sender.declareQueue(QueueSpecification.queue(spec.queue())
                    .durable(DURABLE)
                    .arguments(mainQueueArguments())),
                Flux.fromIterable(spec.bindings())
                    .concatMap(binding -> sender.bind(BindingSpecification.binding()
                        .exchange(binding.exchange())
                        .queue(spec.queue())
                        .routingKey(binding.routingKey()))))
            .then();
    }

    private Mono<AMQP.Exchange.DeclareOk> declareExchange(String exchange, BuiltinExchangeType type) {
        return sender.declareExchange(ExchangeSpecification.exchange(exchange)
                .durable(DURABLE)
                .type(type.getType()))
            .onErrorResume(error -> error instanceof ShutdownSignalException && error.getMessage().contains("reply-code=406, reply-text=PRECONDITION_FAILED"),
                error -> {
                    LOGGER.warn("Exchange `{}` already exists but with different configuration. Ignoring this error. \nError message: {}", exchange, error.getMessage());
                    return Mono.empty();
                });
    }

    private Map<String, Object> mainQueueArguments() {
        QueueDeclaration spec = parameters.queueDeclaration();
        QueueArguments.Builder builder = parameters.queueArguments().get()
            .deadLetter(spec.deadLetterExchange());
        // Queue arguments are immutable once declared: only set x-dead-letter-routing-key for queues that already carry it,
        // otherwise redeclaring an existing queue fails with PRECONDITION_FAILED.
        spec.deadLetterRoutingKey().ifPresent(routingKey -> builder.put("x-dead-letter-routing-key", routingKey));
        if (parameters.singleActiveConsumer()) {
            builder.singleActiveConsumer();
        }
        parameters.consumerTimeout().ifPresent(timeout -> builder.consumerTimeout(timeout.toMillis()));
        return builder.build();
    }

    public void start() {
        consumer = consumeQueue();
    }

    public void restart() {
        Disposable previousConsumer = consumer;
        consumer = consumeQueue();
        Optional.ofNullable(previousConsumer)
            .ifPresent(Disposable::dispose);
    }

    private Disposable consumeQueue() {
        Flux<AcknowledgableDelivery> delivery = delivery();
        Function<AcknowledgableDelivery, Mono<Void>> consume = this::consume;
        return (parameters.concurrency() == 1 ? delivery.concatMap(consume) : delivery.flatMap(consume, parameters.concurrency()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    public Flux<AcknowledgableDelivery> delivery() {
        return Flux.using(receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(parameters.queueDeclaration().queue(), consumeOptions()),
            Receiver::close);
    }

    private ConsumeOptions consumeOptions() {
        return parameters.qos()
            .map(qos -> new ConsumeOptions().qos(qos))
            .orElseGet(ConsumeOptions::new);
    }

    private Mono<Void> consume(AcknowledgableDelivery delivery) {
        return parameters.handleDelivery().apply(delivery)
            .doOnSuccess(result -> delivery.ack())
            .onErrorResume(error -> {
                LOGGER.error("Error when consuming message on queue `{}`", parameters.queueDeclaration().queue(), error);
                delivery.nack(REQUEUE_ON_NACK);
                return Mono.empty();
            });
    }

    @PreDestroy
    @Override
    public void close() {
        Optional.ofNullable(consumer).ifPresent(Disposable::dispose);
    }
}
