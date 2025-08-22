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
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;
import com.linagora.tmail.saas.model.SaaSPlan;
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

public class SaaSSubscriptionConsumer implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSSubscriptionConsumer.class);
    private static final boolean REQUEUE_ON_NACK = true;
    public static final String SAAS_SUBSCRIPTION_QUEUE = "tmail-saas-subscription";
    public static final String SAAS_SUBSCRIPTION_DEAD_LETTER_QUEUE = "tmail-saas-subscription-dead-letter";

    private final ReceiverProvider receiverProvider;
    private final Sender sender;
    private final RabbitMQConfiguration rabbitMQConfiguration;
    private final TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration;
    private final SaaSSubscriptionRabbitMQConfiguration saasSubscriptionRabbitMQConfiguration;
    private final UsersRepository usersRepository;
    private final SaaSAccountRepository saasAccountRepository;
    private final MaxQuotaManager maxQuotaManager;
    private final UserQuotaRootResolver userQuotaRootResolver;
    private Disposable consumeSubscriptionDisposable;

    @Inject
    public SaaSSubscriptionConsumer(@Named(TWP_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                    @Named(TWP_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration,
                                    TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration,
                                    SaaSSubscriptionRabbitMQConfiguration saasSubscriptionRabbitMQConfiguration,
                                    UsersRepository usersRepository,
                                    SaaSAccountRepository saasAccountRepository,
                                    MaxQuotaManager maxQuotaManager,
                                    UserQuotaRootResolver userQuotaRootResolver) {
        this.receiverProvider = channelPool::createReceiver;
        this.sender = channelPool.getSender();
        this.rabbitMQConfiguration = rabbitMQConfiguration;
        this.twpCommonRabbitMQConfiguration = twpCommonRabbitMQConfiguration;
        this.saasSubscriptionRabbitMQConfiguration = saasSubscriptionRabbitMQConfiguration;
        this.usersRepository = usersRepository;
        this.saasAccountRepository = saasAccountRepository;
        this.maxQuotaManager = maxQuotaManager;
        this.userQuotaRootResolver = userQuotaRootResolver;
    }

    public void init() {
        declareExchangeAndQueue(saasSubscriptionRabbitMQConfiguration.exchange(), SAAS_SUBSCRIPTION_QUEUE, SAAS_SUBSCRIPTION_DEAD_LETTER_QUEUE);
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
                    .routingKey(saasSubscriptionRabbitMQConfiguration.routingKey())))
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
        consumeSubscriptionDisposable = consumeSubscriptionQueue();
    }

    public void restartConsumer() {
        Disposable previousConsumer = consumeSubscriptionDisposable;
        consumeSubscriptionDisposable = consumeSubscriptionQueue();
        Optional.ofNullable(previousConsumer)
            .ifPresent(Disposable::dispose);
    }

    private Disposable consumeSubscriptionQueue() {
        return delivery(SAAS_SUBSCRIPTION_QUEUE)
            .concatMap(delivery -> consumeSubscriptionUpdate(delivery, delivery.getBody()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    public Flux<AcknowledgableDelivery> delivery(String queue) {
        return Flux.using(receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(queue, new ConsumeOptions()
                .qos(DEFAULT_CONCURRENCY)),
            Receiver::close);
    }

    private Mono<Void> consumeSubscriptionUpdate(AcknowledgableDelivery ackDelivery, byte[] messagePayload) {
        return Mono.fromCallable(() -> SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(messagePayload))
            .flatMap(this::handleSubscriptionMessage)
            .doOnSuccess(result -> {
                LOGGER.debug("Consumed SaaS subscription message successfully");
                ackDelivery.ack();
            })
            .onErrorResume(error -> {
                LOGGER.error("Error when consuming SaaS subscription message", error);
                ackDelivery.nack(!REQUEUE_ON_NACK);
                return Mono.empty();
            });
    }

    private Mono<Void> handleSubscriptionMessage(SaaSSubscriptionMessage subscriptionMessage) {
        return Mono.fromCallable(() -> usersRepository.getUserByName(Username.of(subscriptionMessage.username())))
            .map(User::getUserName)
            .flatMap(username -> updatePlan(username, subscriptionMessage.planName())
                .then(updateStorageQuota(username, subscriptionMessage.mail().storageQuota()))
                .doOnSuccess(success -> LOGGER.info("Updated SaaS subscription for user: {}, isPaying: {}, planName: {}, storageQuota: {}",
                    username, subscriptionMessage.isPaying(), subscriptionMessage.planName(), subscriptionMessage.mail().storageQuota())));
    }

    private Mono<Void> updatePlan(Username username, String planName) {
        return Mono.from(saasAccountRepository.upsertSaasAccount(username,
            new SaaSAccount(new SaaSPlan(planName))));
    }

    private Mono<Void> updateStorageQuota(Username username, Long storageQuota) {
        return Mono.from(maxQuotaManager.setMaxStorageReactive(userQuotaRootResolver.forUser(username), asQuotaSizeLimit(storageQuota)));
    }

    private QuotaSizeLimit asQuotaSizeLimit(Long storageQuota) {
        if (storageQuota == -1) {
            return QuotaSizeLimit.unlimited();
        }
        return QuotaSizeLimit.size(storageQuota);
    }

    @Override
    public void close() {
        Optional.ofNullable(consumeSubscriptionDisposable).ifPresent(Disposable::dispose);
    }
}
