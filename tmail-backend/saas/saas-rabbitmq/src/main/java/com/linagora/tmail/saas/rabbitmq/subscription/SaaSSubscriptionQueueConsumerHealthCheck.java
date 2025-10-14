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
import static com.linagora.tmail.saas.rabbitmq.subscription.SaaSDomainSubscriptionConsumer.SAAS_DOMAIN_SUBSCRIPTION_QUEUE;
import static com.linagora.tmail.saas.rabbitmq.subscription.SaaSSubscriptionConsumer.SAAS_SUBSCRIPTION_QUEUE;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.RabbitMQManagementAPI;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class SaaSSubscriptionQueueConsumerHealthCheck implements HealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSSubscriptionQueueConsumerHealthCheck.class);
    public static final ComponentName COMPONENT_NAME = new ComponentName("SaaSSubscriptionQueueConsumerHealthCheck");
    private static final String DEFAULT_VHOST = "/";
    private static final List<String> SAAS_SUBSCRIPTION_QUEUES = ImmutableList.of(
        SAAS_SUBSCRIPTION_QUEUE,
        SAAS_DOMAIN_SUBSCRIPTION_QUEUE);

    private final RabbitMQConfiguration twpRabbitMQConfiguration;
    private final SaaSSubscriptionConsumer saaSSubscriptionConsumer;
    private final SaaSDomainSubscriptionConsumer saaSDomainSubscriptionConsumer;
    private final RabbitMQManagementAPI api;

    @Inject
    public SaaSSubscriptionQueueConsumerHealthCheck(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration,
                                                    SaaSSubscriptionConsumer saaSSubscriptionConsumer,
                                                    SaaSDomainSubscriptionConsumer saaSDomainSubscriptionConsumer) {
        this.twpRabbitMQConfiguration = twpRabbitMQConfiguration;
        this.api = RabbitMQManagementAPI.from(twpRabbitMQConfiguration);
        this.saaSSubscriptionConsumer = saaSSubscriptionConsumer;
        this.saaSDomainSubscriptionConsumer = saaSDomainSubscriptionConsumer;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Flux.fromIterable(SAAS_SUBSCRIPTION_QUEUES)
            .map(this::checkQueueConsumer)
            .flatMap(queueConsumerDetails -> {
                if (queueConsumerDetails.getRight().isEmpty()) {
                    return restartSaaSSubscriptionConsumer(queueConsumerDetails.getLeft());
                }
                return Mono.fromCallable(() -> Result.healthy(COMPONENT_NAME));
            })
            .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking SaaSSubscriptionQueuesConsumerHealthCheck", e)))
            .reduce(SaaSSubscriptionUtils::combine)
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Pair<String, List<RabbitMQManagementAPI.ConsumerDetails>> checkQueueConsumer(String queueName) {
        return Pair.of(queueName, api.queueDetails(twpRabbitMQConfiguration.getVhost().orElse(DEFAULT_VHOST), queueName)
            .getConsumerDetails());
    }

    private Mono<Result> restartSaaSSubscriptionConsumer(String queue) {
        LOGGER.warn("SaaSSubscriptionQueueConsumerHealthCheck found no consumers, restarting the consumer for queue {}", queue);

        return Mono.fromRunnable(() -> restartQueueConsumer(queue))
            .thenReturn(Result.degraded(COMPONENT_NAME, String.format("The SaaS subscription queue %s has no consumers", queue)))
            .onErrorResume(error -> {
                LOGGER.error("Error while restarting SaaS subscription consumer for queue {}", queue, error);
                return Mono.fromCallable(() -> Result.degraded(COMPONENT_NAME, String.format("The SaaS subscription queue %s has no consumers", queue)));
            });
    }

    private void restartQueueConsumer(String queue) {
        if (queue.equalsIgnoreCase(SAAS_SUBSCRIPTION_QUEUE)) {
            saaSSubscriptionConsumer.restartConsumer();
        }
        if (queue.equalsIgnoreCase(SAAS_DOMAIN_SUBSCRIPTION_QUEUE)) {
            saaSDomainSubscriptionConsumer.restartConsumer();
        }
    }
}
