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

package com.linagora.tmail.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.events.TmailRabbitEventBusConfiguration;
import org.junit.jupiter.api.Test;

public class RabbitMQAndRedisEventBusMonitorQueuesTest {
    @Test
    void groupQueuesToMonitorWhenNoPartitioning() {
        assertThat(RabbitMQAndRedisEventBusModule.computeEventBusGroupQueuesToMonitor(new TmailRabbitEventBusConfiguration(1)))
            .hasSize(4)
            .containsExactlyInAnyOrder(
                "mailboxEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "jmapEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "contentDeletionEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "tmailEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup");
    }

    @Test
    void groupQueuesToMonitorShouldTakeInToAccountPartitions() {
        assertThat(RabbitMQAndRedisEventBusModule.computeEventBusGroupQueuesToMonitor(new TmailRabbitEventBusConfiguration(3)))
            .hasSize(12)
            .containsExactlyInAnyOrder(
                "mailboxEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "mailboxEvent-1-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "mailboxEvent-2-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "jmapEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "jmapEvent-1-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "jmapEvent-2-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "contentDeletionEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "contentDeletionEvent-1-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "contentDeletionEvent-2-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "tmailEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "tmailEvent-1-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "tmailEvent-2-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup");
    }
}
