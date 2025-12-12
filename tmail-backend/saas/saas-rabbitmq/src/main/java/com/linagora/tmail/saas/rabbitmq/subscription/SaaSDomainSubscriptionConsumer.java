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
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.Closeable;
import java.time.Duration;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage;
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

public class SaaSDomainSubscriptionConsumer implements Closeable, Startable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSDomainSubscriptionConsumer.class);
    private static final boolean REQUEUE_ON_NACK = true;
    public static final String SAAS_DOMAIN_SUBSCRIPTION_QUEUE = "tmail-saas-domain-subscription";
    public static final String SAAS_DOMAIN_SUBSCRIPTION_DEAD_LETTER_QUEUE = "tmail-saas-domain-subscription-dead-letter";

    private final ReceiverProvider receiverProvider;
    private final Sender sender;
    private final RabbitMQConfiguration rabbitMQConfiguration;
    private final TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration;
    private final SaaSSubscriptionRabbitMQConfiguration saasSubscriptionRabbitMQConfiguration;
    private final DomainList domainList;
    private final MaxQuotaManager maxQuotaManager;
    private final RateLimitingRepository rateLimitingRepository;
    private Disposable consumeSubscriptionDisposable;

    @Inject
    public SaaSDomainSubscriptionConsumer(@Named(TWP_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                          @Named(TWP_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration,
                                          TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                                          SaaSSubscriptionRabbitMQConfiguration saasSubscriptionRabbitMQConfiguration,
                                          DomainList domainList,
                                          MaxQuotaManager maxQuotaManager,
                                          RateLimitingRepository rateLimitingRepository) {
        this.receiverProvider = channelPool::createReceiver;
        this.sender = channelPool.getSender();
        this.rabbitMQConfiguration = rabbitMQConfiguration;
        this.twpCommonRabbitMQConfiguration = twpCommonRabbitMQConfiguration;
        this.saasSubscriptionRabbitMQConfiguration = saasSubscriptionRabbitMQConfiguration;
        this.domainList = domainList;
        this.maxQuotaManager = maxQuotaManager;
        this.rateLimitingRepository = rateLimitingRepository;
    }

    public void init() {
        declareExchangeAndQueue();
        startConsumer();
    }

    public void declareExchangeAndQueue() {
        Flux.concat(
                declareExchange(saasSubscriptionRabbitMQConfiguration.exchange()),
                declareExchange(saasSubscriptionRabbitMQConfiguration.configurationExchange()),
                sender.declareQueue(QueueSpecification
                    .queue(SAAS_DOMAIN_SUBSCRIPTION_DEAD_LETTER_QUEUE)
                    .durable(DURABLE)
                    .arguments(queueArgumentSupplier()
                        .build())),
                sender.declareQueue(QueueSpecification
                    .queue(SAAS_DOMAIN_SUBSCRIPTION_QUEUE)
                    .durable(DURABLE)
                    .arguments(queueArgumentSupplier()
                        .deadLetter(SAAS_DOMAIN_SUBSCRIPTION_DEAD_LETTER_QUEUE)
                        .singleActiveConsumer()
                        .consumerTimeout(Duration.ofMinutes(10L).toMillis())
                        .build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(saasSubscriptionRabbitMQConfiguration.exchange())
                    .queue(SAAS_DOMAIN_SUBSCRIPTION_QUEUE)
                    .routingKey(saasSubscriptionRabbitMQConfiguration.domainRoutingKey())),
                sender.bind(BindingSpecification.binding()
                    .exchange(saasSubscriptionRabbitMQConfiguration.configurationExchange())
                    .queue(SAAS_DOMAIN_SUBSCRIPTION_QUEUE)
                    .routingKey(saasSubscriptionRabbitMQConfiguration.domainConfigurationRoutingKey())))
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
        consumeSubscriptionDisposable = consumeDomainSubscriptionQueue();
    }

    public void restartConsumer() {
        Disposable previousConsumer = consumeSubscriptionDisposable;
        consumeSubscriptionDisposable = consumeDomainSubscriptionQueue();
        Optional.ofNullable(previousConsumer)
            .ifPresent(Disposable::dispose);
    }

    private Disposable consumeDomainSubscriptionQueue() {
        return delivery(SAAS_DOMAIN_SUBSCRIPTION_QUEUE)
            .concatMap(delivery -> consumeDomainSubscriptionUpdate(delivery, delivery.getBody()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    public Flux<AcknowledgableDelivery> delivery(String queue) {
        return Flux.using(receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(queue, new ConsumeOptions()
                .qos(DEFAULT_CONCURRENCY)),
            Receiver::close);
    }

    private Mono<Void> consumeDomainSubscriptionUpdate(AcknowledgableDelivery ackDelivery, byte[] messagePayload) {
        return Mono.fromCallable(() -> SaaSSubscriptionDeserializer.parseAMQPDomainMessage(messagePayload))
            .flatMap(this::handleDomainSubscriptionMessage)
            .doOnSuccess(result -> ackDelivery.ack())
            .onErrorResume(error -> {
                LOGGER.error("Error when consuming SaaS domain subscription message", error);
                ackDelivery.nack(!REQUEUE_ON_NACK);
                return Mono.empty();
            });
    }

    private Mono<Void> handleDomainSubscriptionMessage(SaaSDomainSubscriptionMessage domainSubscriptionMessage) {
        return switch (domainSubscriptionMessage) {
            case SaaSDomainValidSubscriptionMessage message  -> handleDomainValidSubscriptionMessage(message);
            case SaaSDomainCancelSubscriptionMessage message -> handleDomainCancelSubscriptionMessage(message);
            default                                          -> throw new IllegalArgumentException("Unrecognized SaaS domain subscription message");
        };
    }

    private Mono<Void> handleDomainCancelSubscriptionMessage(SaaSDomainCancelSubscriptionMessage domainCancelSubscriptionMessage) {
        if (!domainCancelSubscriptionMessage.enabled()) {
            Domain domain = Domain.of(domainCancelSubscriptionMessage.domain());
            return removeDomainIfExists(domain)
                .doOnSuccess(success -> LOGGER.info("Cancelled SaaS subscription for domain: {}", domain));
        }
        return Mono.empty();
    }

    private Mono<Void> removeDomainIfExists(Domain domain) {
        return Mono.fromRunnable(Throwing.runnable(() -> domainList.removeDomain(domain)))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .then();
    }

    private Mono<Void> handleDomainValidSubscriptionMessage(SaaSDomainValidSubscriptionMessage message) {
        return createDomainIfValidated(message)
            .then(applyDomainSettings(message));
    }

    private Mono<Void> createDomainIfValidated(SaaSDomainValidSubscriptionMessage message) {
        Domain domain = Domain.of(message.domain());
        if (message.validated().orElse(false)) {
            return addDomainIfNotExist(domain);
        }
        return Mono.empty();
    }

    private Mono<Void> applyDomainSettings(SaaSDomainValidSubscriptionMessage message) {
        Domain domain = Domain.of(message.domain());

        return message.features().mail().map(mailSettings ->
            updateStorageDomainQuota(domain, mailSettings.storageQuota())
                .then(updateRateLimiting(domain, mailSettings.rateLimitingDefinition()))
                .doOnSuccess(success -> LOGGER.info("Updated SaaS subscription for domain: {}, storageQuota: {}, rateLimiting: {}",
                    domain, mailSettings.storageQuota(), mailSettings.rateLimitingDefinition()))).orElse(Mono.empty());
    }

    private Mono<Void> addDomainIfNotExist(Domain domain) {
        return Mono.from(domainList.containsDomainReactive(domain))
            .flatMap(alreadyExists -> {
                if (alreadyExists) {
                    return Mono.empty();
                }
                return Mono.fromRunnable(Throwing.runnable(() -> domainList.addDomain(domain)))
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                    .then();
            });
    }

    private Mono<Void> updateStorageDomainQuota(Domain domain, Long storageQuota) {
        return Mono.from(maxQuotaManager.setDomainMaxStorageReactive(domain, SaaSSubscriptionUtils.asQuotaSizeLimit(storageQuota)));
    }

    private Mono<Void> updateRateLimiting(Domain domain, RateLimitingDefinition rateLimiting) {
        return Mono.from(rateLimitingRepository.setRateLimiting(domain, rateLimiting));
    }

    @Override
    public void close() {
        Optional.ofNullable(consumeSubscriptionDisposable).ifPresent(Disposable::dispose);
    }
}
