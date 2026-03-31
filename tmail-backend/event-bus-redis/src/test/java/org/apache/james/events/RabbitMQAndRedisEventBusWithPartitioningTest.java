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

import static org.apache.james.events.EventBusTestFixture.EVENT;
import static org.apache.james.events.EventBusTestFixture.KEY_1;
import static org.apache.james.events.EventBusTestFixture.NO_KEYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;

import java.util.List;
import java.util.stream.IntStream;

import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.backends.redis.StandaloneRedisConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

public class RabbitMQAndRedisEventBusWithPartitioningTest extends RabbitMQAndRedisEventBusContractTest {
    private static final int PARTITION_COUNT = 3;
    private static final int EVENT_COUNT = 50;

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    @Override
    RedisConfiguration redisConfiguration() {
        return StandaloneRedisConfiguration.from(redisExtension.dockerRedis().redisURI().toString());
    }

    @Override
    public void pauseRedis() {
        redisExtension.dockerRedis().pause();
    }

    @Override
    public void unpauseRedis() {
        redisExtension.dockerRedis().unPause();
    }

    @Override
    List<NamingStrategy> namingStrategies() {
        return new TmailNamingStrategyFactory(TEST_EVENT_BUS, new TmailRabbitEventBusConfiguration(PARTITION_COUNT)).namingStrategies();
    }

    @Override
    List<NamingStrategy> mailboxEventNamingStrategies() {
        return new TmailNamingStrategyFactory(new EventBusName("mailboxEvent"),
            new TmailRabbitEventBusConfiguration(PARTITION_COUNT)).namingStrategies();
    }

    @Override
    List<String> expectedGroupQueuesNames() {
        // to detect breaking change on Group queues name
        return List.of(
            "mailboxEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
            "mailboxEvent-1-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
            "mailboxEvent-2-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup");
    }

    @Test
    void groupListenersShouldConsumeAllEventsWithPartitionedEventBus() {
        EventCollector listener = new EventCollector();
        eventBus().register(listener);

        IntStream.range(0, EVENT_COUNT)
            .forEach(index -> eventBus().dispatch(EVENT, NO_KEYS).block());

        await().timeout(TEN_SECONDS)
            .untilAsserted(() -> assertThat(listener.getEvents()).hasSize(EVENT_COUNT));
    }

    @Test
    void keyListenersShouldConsumeAllEventsWithPartitionedEventBus() {
        EventBusTestFixture.EventListenerCountingSuccessfulExecution listener = new EventBusTestFixture.EventListenerCountingSuccessfulExecution();

        Mono.from(eventBus().register(listener, KEY_1)).block();

        IntStream.range(0, EVENT_COUNT)
            .forEach(index -> eventBus().dispatch(EVENT, KEY_1).block());

        await().timeout(TEN_SECONDS)
            .untilAsserted(() -> assertThat(listener.numberOfEventCalls()).isEqualTo(EVENT_COUNT));
    }
}
