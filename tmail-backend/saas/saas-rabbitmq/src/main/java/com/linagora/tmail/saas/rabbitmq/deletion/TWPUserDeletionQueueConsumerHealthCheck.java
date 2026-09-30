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

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.RabbitMQManagementAPI;
import com.linagora.tmail.rabbitmq.ManagedRabbitMQConsumer;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class TWPUserDeletionQueueConsumerHealthCheck implements HealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(TWPUserDeletionQueueConsumerHealthCheck.class);
    public static final ComponentName COMPONENT_NAME = new ComponentName("TWPUserDeletionQueueConsumerHealthCheck");
    private static final String DEFAULT_VHOST = "/";

    private final RabbitMQConfiguration twpRabbitMQConfiguration;
    private final ManagedRabbitMQConsumer twpUserDeletionConsumer;
    private final RabbitMQManagementAPI managementAPI;
    private final String queueName;

    public TWPUserDeletionQueueConsumerHealthCheck(RabbitMQConfiguration twpRabbitMQConfiguration,
                                                   ManagedRabbitMQConsumer twpUserDeletionConsumer,
                                                   String queueName) {
        this.twpRabbitMQConfiguration = twpRabbitMQConfiguration;
        this.managementAPI = RabbitMQManagementAPI.from(twpRabbitMQConfiguration);
        this.twpUserDeletionConsumer = twpUserDeletionConsumer;
        this.queueName = queueName;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Mono.fromCallable(() -> managementAPI.queueDetails(twpRabbitMQConfiguration.getVhost().orElse(DEFAULT_VHOST), queueName)
                .getConsumerDetails())
            .flatMap(consumers -> {
                if (consumers.isEmpty()) {
                    return restartTWPUserDeletionConsumer();
                }
                return Mono.fromCallable(() -> Result.healthy(COMPONENT_NAME));
            })
            .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking TWPUserDeletionQueueConsumerHealthCheck", e)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Result> restartTWPUserDeletionConsumer() {
        LOGGER.warn("TWPUserDeletionQueueConsumerHealthCheck found no consumers, restarting the consumer");

        return Mono.fromRunnable(twpUserDeletionConsumer::restart)
            .thenReturn(Result.degraded(COMPONENT_NAME, "The TWP user deletion queue has no consumers"))
            .onErrorResume(error -> {
                LOGGER.error("Error while restarting TWP user deletion consumer", error);
                return Mono.fromCallable(() -> Result.degraded(COMPONENT_NAME, "The TWP user deletion queue has no consumers"));
            });
    }
}
