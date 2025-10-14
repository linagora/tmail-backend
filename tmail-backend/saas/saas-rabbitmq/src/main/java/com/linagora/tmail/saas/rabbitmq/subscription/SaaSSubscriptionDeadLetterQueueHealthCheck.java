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
import static com.linagora.tmail.saas.rabbitmq.subscription.SaaSDomainSubscriptionConsumer.SAAS_DOMAIN_SUBSCRIPTION_DEAD_LETTER_QUEUE;
import static com.linagora.tmail.saas.rabbitmq.subscription.SaaSSubscriptionConsumer.SAAS_SUBSCRIPTION_DEAD_LETTER_QUEUE;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQManagementAPI;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class SaaSSubscriptionDeadLetterQueueHealthCheck implements HealthCheck {
    public static final ComponentName COMPONENT_NAME = new ComponentName("SaaSSubscriptionDeadLetterQueueHealthCheck");
    private static final String DEFAULT_VHOST = "/";
    private static final List<String> DEAD_LETTER_QUEUES = ImmutableList.of(
        SAAS_SUBSCRIPTION_DEAD_LETTER_QUEUE,
        SAAS_DOMAIN_SUBSCRIPTION_DEAD_LETTER_QUEUE);

    private final RabbitMQConfiguration twpRabbitMQConfiguration;
    private final RabbitMQManagementAPI api;

    @Inject
    public SaaSSubscriptionDeadLetterQueueHealthCheck(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration) {
        this.twpRabbitMQConfiguration = twpRabbitMQConfiguration;
        this.api = RabbitMQManagementAPI.from(twpRabbitMQConfiguration);
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Flux.fromIterable(DEAD_LETTER_QUEUES)
            .map(this::checkQueueLength)
            .map(queueDetails -> {
                if (queueDetails.getRight() != 0) {
                    return Result.degraded(COMPONENT_NAME, String.format("RabbitMQ dead letter queue %s contain events. This might indicate transient failure on event processing.", queueDetails.getLeft()));
                }
                return Result.healthy(COMPONENT_NAME);
            })
            .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking SaaSSubscriptionDeadLetterQueueHealthCheck", e)))
            .reduce(SaaSSubscriptionUtils::combine)
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Pair<String, Long> checkQueueLength(String queueName) {
        return Pair.of(queueName, api.queueDetails(twpRabbitMQConfiguration.getVhost().orElse(DEFAULT_VHOST), queueName)
            .getQueueLength());
    }
}
