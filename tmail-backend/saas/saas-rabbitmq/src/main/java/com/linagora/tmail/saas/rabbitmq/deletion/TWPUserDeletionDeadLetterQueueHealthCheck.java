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
import org.apache.james.backends.rabbitmq.RabbitMQManagementAPI;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class TWPUserDeletionDeadLetterQueueHealthCheck implements HealthCheck {
    public static final ComponentName COMPONENT_NAME = new ComponentName("TWPUserDeletionDeadLetterQueueHealthCheck");
    private static final String DEFAULT_VHOST = "/";

    private final RabbitMQConfiguration twpRabbitMQConfiguration;
    private final RabbitMQManagementAPI managementAPI;
    private final String queueName;

    public TWPUserDeletionDeadLetterQueueHealthCheck(RabbitMQConfiguration twpRabbitMQConfiguration,
                                                     String queueName) {
        this.twpRabbitMQConfiguration = twpRabbitMQConfiguration;
        this.managementAPI = RabbitMQManagementAPI.from(twpRabbitMQConfiguration);
        this.queueName = queueName;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Mono.fromCallable(() -> managementAPI.queueDetails(twpRabbitMQConfiguration.getVhost().orElse(DEFAULT_VHOST), queueName)
                .getQueueLength())
            .map(queueSize -> {
                if (queueSize != 0) {
                    return Result.degraded(COMPONENT_NAME, "RabbitMQ dead letter queue of TWP user deletion contain events. This might indicate transient failure on event processing.");
                }
                return Result.healthy(COMPONENT_NAME);
            })
            .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking TWPUserDeletionDeadLetterQueueHealthCheck", e)))
            .subscribeOn(Schedulers.boundedElastic());
    }
}
