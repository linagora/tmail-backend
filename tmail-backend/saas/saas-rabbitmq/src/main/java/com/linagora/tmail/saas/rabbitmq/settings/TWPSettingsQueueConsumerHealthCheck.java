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

import static com.linagora.tmail.saas.rabbitmq.TWPConstants.TWP_INJECTION_KEY;
import static com.linagora.tmail.saas.rabbitmq.settings.TWPSettingsConsumer.TWP_SETTINGS_QUEUE;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.RabbitMQManagementAPI;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class TWPSettingsQueueConsumerHealthCheck implements HealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(TWPSettingsQueueConsumerHealthCheck.class);
    public static final ComponentName COMPONENT_NAME = new ComponentName("TWPSettingsQueueConsumerHealthCheck");
    private static final String DEFAULT_VHOST = "/";

    private final RabbitMQConfiguration twpRabbitMQConfiguration;
    private final TWPSettingsConsumer twpSettingsConsumer;
    private final RabbitMQManagementAPI api;

    @Inject
    public TWPSettingsQueueConsumerHealthCheck(@Named(TWP_INJECTION_KEY) RabbitMQConfiguration twpRabbitMQConfiguration,
                                               TWPSettingsConsumer twpSettingsConsumer) {
        this.twpRabbitMQConfiguration = twpRabbitMQConfiguration;
        this.api = RabbitMQManagementAPI.from(twpRabbitMQConfiguration);
        this.twpSettingsConsumer = twpSettingsConsumer;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Mono.fromCallable(() -> api.queueDetails(twpRabbitMQConfiguration.getVhost().orElse(DEFAULT_VHOST), TWP_SETTINGS_QUEUE)
                .getConsumerDetails())
            .flatMap(consumers -> {
                if (consumers.isEmpty()) {
                    return restartTWPSettingsConsumer();
                }
                return Mono.fromCallable(() -> Result.healthy(COMPONENT_NAME));
            })
            .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking TWPSettingsQueueConsumerHealthCheck", e)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Result> restartTWPSettingsConsumer() {
        LOGGER.warn("TWPSettingsQueueConsumerHealthCheck found no consumers, restarting the consumer");

        return Mono.fromRunnable(twpSettingsConsumer::restartConsumer)
            .thenReturn(Result.degraded(COMPONENT_NAME, "The TWP settings queue has no consumers"))
            .onErrorResume(error -> {
                LOGGER.error("Error while restarting TWP settings consumer", error);
                return Mono.fromCallable(() -> Result.degraded(COMPONENT_NAME, "The TWP settings queue has no consumers"));
            });
    }
}
