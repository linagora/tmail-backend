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

import org.junit.jupiter.api.Test;

class RedisKeyEventDispatcherTest {
    private static final EventBusId EVENT_BUS_ID = EventBusId.of("00000000-0000-0000-0000-000000000123");

    @Test
    void baseEventBusNameShouldBeExtractedFromDefaultNamingStrategy() {
        EventBusName baseEventBusName = RedisKeyEventDispatcher.baseEventBusName(
            new DefaultNamingStrategy(new EventBusName("tmailEvent")),
            EVENT_BUS_ID);

        assertThat(baseEventBusName).isEqualTo(new EventBusName("tmailEvent"));
    }

    @Test
    void baseEventBusNameShouldBeExtractedFromPartitionAwareNamingStrategy() {
        EventBusName baseEventBusName = RedisKeyEventDispatcher.baseEventBusName(
            new PartitionAwareNamingStrategy(new EventBusName("mailboxEvent"), 1),
            EVENT_BUS_ID);

        assertThat(baseEventBusName).isEqualTo(new EventBusName("mailboxEvent"));
    }
}
