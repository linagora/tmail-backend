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
 ********************************************************************/

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.core.healthcheck.ResultStatus;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public class AggregatedRabbitEventBusConsumerHealthCheckTest {
    @Test
    void componentNameShouldReusePartitionZeroNaming() {
        AggregatedRabbitEventBusConsumerHealthCheck testee = new AggregatedRabbitEventBusConsumerHealthCheck(mock(EventBus.class),
            List.of(new DefaultNamingStrategy(new EventBusName("mailboxEvent")),
                new PartitionAwareNamingStrategy(new EventBusName("mailboxEvent"), 2)),
            mock(SimpleConnectionPool.class),
            TmailGroupRegistrationHandler.GROUP);

        assertThat(testee.componentName()).isEqualTo(new ComponentName("EventbusConsumers-mailboxEvent"));
    }

    @Test
    void checkShouldMergeDelegatedStatusesAndCauses() {
        HealthCheck healthy = healthCheck(Result.healthy(new ComponentName("EventbusConsumers-mailboxEvent")));
        HealthCheck degraded = healthCheck(Result.degraded(new ComponentName("EventbusConsumers-mailboxEvent-1"), "No consumers on mailboxEvent-1-workQueue"));
        HealthCheck unhealthy = healthCheck(Result.unhealthy(new ComponentName("EventbusConsumers-mailboxEvent-2"), "Connection lost"));

        AggregatedRabbitEventBusConsumerHealthCheck testee = new AggregatedRabbitEventBusConsumerHealthCheck(new ComponentName("EventbusConsumers-mailboxEvent"),
            List.of(healthy, degraded, unhealthy));

        Result result = testee.check().block();

        assertThat(result.getStatus()).isEqualTo(ResultStatus.UNHEALTHY);
        assertThat(result.getCause()).hasValue("No consumers on mailboxEvent-1-workQueue; Connection lost");
    }

    @Test
    void checkShouldBeHealthyWhenAllPartitionsAreHealthy() {
        HealthCheck firstHealthy = healthCheck(Result.healthy(new ComponentName("EventbusConsumers-mailboxEvent")));
        HealthCheck secondHealthy = healthCheck(Result.healthy(new ComponentName("EventbusConsumers-mailboxEvent-1")));

        AggregatedRabbitEventBusConsumerHealthCheck testee = new AggregatedRabbitEventBusConsumerHealthCheck(new ComponentName("EventbusConsumers-mailboxEvent"),
            List.of(firstHealthy, secondHealthy));

        Result result = testee.check().block();

        assertThat(result.getStatus()).isEqualTo(ResultStatus.HEALTHY);
    }

    @Test
    void checkShouldBeDegradedWhenOnePartitionIsDegraded() {
        HealthCheck firstHealthy = healthCheck(Result.healthy(new ComponentName("EventbusConsumers-mailboxEvent")));
        HealthCheck secondHealthy = healthCheck(Result.healthy(new ComponentName("EventbusConsumers-mailboxEvent-1")));
        HealthCheck degraded = healthCheck(Result.degraded(new ComponentName("EventbusConsumers-mailboxEvent-2"), "No consumers on mailboxEvent-2-workQueue"));

        AggregatedRabbitEventBusConsumerHealthCheck testee = new AggregatedRabbitEventBusConsumerHealthCheck(new ComponentName("EventbusConsumers-mailboxEvent"),
            List.of(firstHealthy, secondHealthy, degraded));

        Result result = testee.check().block();

        assertThat(result.getStatus()).isEqualTo(ResultStatus.DEGRADED);
        assertThat(result.getCause()).hasValue("No consumers on mailboxEvent-2-workQueue");
    }

    private HealthCheck healthCheck(Result result) {
        return new HealthCheck() {
            @Override
            public ComponentName componentName() {
                return result.getComponentName();
            }

            @Override
            public Mono<Result> check() {
                return Mono.just(result);
            }
        };
    }
}
