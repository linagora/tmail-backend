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

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

public class TmailNamingStrategyFactoryTest {
    private static final EventBusName EVENT_BUS_NAME = new EventBusName("mailboxEvent");
    private static final EventBusId EVENT_BUS_ID = EventBusId.of("00000000-0000-0000-0000-000000000123");
    private static final Group GROUP = new Group() {
    };

    @Test
    void shouldReturnSingleDefaultNamingStrategyWhenPartitionCountIsOne() {
        TmailNamingStrategyFactory testee = new TmailNamingStrategyFactory(EVENT_BUS_NAME, new TmailRabbitEventBusConfiguration(1));

        List<NamingStrategy> strategies = testee.namingStrategies();

        assertThat(strategies).hasSize(1);
        assertThat(strategies.getFirst()).isEqualTo(new DefaultNamingStrategy(EVENT_BUS_NAME));
    }

    @Test
    void shouldReturnDefaultAndPartitionAwareStrategiesWhenPartitionCountIsThree() {
        TmailNamingStrategyFactory testee = new TmailNamingStrategyFactory(EVENT_BUS_NAME, new TmailRabbitEventBusConfiguration(3));

        List<NamingStrategy> strategies = testee.namingStrategies();

        assertThat(strategies).hasSize(3);
        assertThat(strategies.get(0)).isInstanceOf(DefaultNamingStrategy.class);
        assertThat(strategies.get(1)).isInstanceOf(PartitionAwareNamingStrategy.class);
        assertThat(strategies.get(2)).isInstanceOf(PartitionAwareNamingStrategy.class);

        assertThat(strategies.get(0).exchange()).isEqualTo("mailboxEvent-exchange");
        assertThat(strategies.get(1).exchange()).isEqualTo("mailboxEvent-1-exchange");
        assertThat(strategies.get(2).exchange()).isEqualTo("mailboxEvent-2-exchange");

        assertThat(strategies.get(0).workQueue(GROUP).asString()).isEqualTo("mailboxEvent-workQueue-" + GROUP.asString());
        assertThat(strategies.get(1).workQueue(GROUP).asString()).isEqualTo("mailboxEvent-1-workQueue-" + GROUP.asString());
        assertThat(strategies.get(2).workQueue(GROUP).asString()).isEqualTo("mailboxEvent-2-workQueue-" + GROUP.asString());
    }

    @Test
    void shouldKeepDeadLetterResourcesSharedAcrossPartitions() {
        TmailNamingStrategyFactory testee = new TmailNamingStrategyFactory(EVENT_BUS_NAME, new TmailRabbitEventBusConfiguration(3));

        List<NamingStrategy> strategies = testee.namingStrategies();

        assertThat(strategies)
            .extracting(NamingStrategy::deadLetterExchange)
            .containsOnly("mailboxEvent-dead-letter-exchange");
        assertThat(strategies)
            .extracting(strategy -> strategy.deadLetterQueue().getName())
            .containsOnly("mailboxEvent-dead-letter-queue");
    }

    @Test
    void notificationQueueNameShouldBeTheSameAcrossPartitions() {
        TmailNamingStrategyFactory testee = new TmailNamingStrategyFactory(EVENT_BUS_NAME, new TmailRabbitEventBusConfiguration(3));

        List<NamingStrategy> strategies = testee.namingStrategies();

        assertThat(strategies)
            .extracting(strategy -> strategy.queueName(EVENT_BUS_ID).asString())
            .containsOnly("mailboxEvent-eventbus-00000000-0000-0000-0000-000000000123");
    }
}
