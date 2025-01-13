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

import static org.apache.james.events.KeyChannelMessage.REDIS_CHANNEL_MESSAGE_DELIMITER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class KeyChannelMessageTest {
    private static final UUID UUID_1 = UUID.fromString("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    private static final EventBusId EVENT_BUS_ID = EventBusId.of(UUID_1);
    private static final RoutingKeyConverter.RoutingKey ROUTING_KEY = RoutingKeyConverter.RoutingKey.of(new EventBusTestFixture.TestRegistrationKey("a"));
    private static final String EVENT_AS_JSON = """
        {"eventId": "123"}""";
    private static final String VALID_CHANNEL_MESSAGE = EVENT_BUS_ID.asString() + REDIS_CHANNEL_MESSAGE_DELIMITER + ROUTING_KEY.asString() + REDIS_CHANNEL_MESSAGE_DELIMITER + EVENT_AS_JSON;
    private static final String INVALID_CHANNEL_MESSAGE = "whatever";

    @Test
    void serializeShouldSucceed() {
        assertThat(KeyChannelMessage.from(EVENT_BUS_ID, ROUTING_KEY, EVENT_AS_JSON)
            .serialize())
            .isEqualTo(VALID_CHANNEL_MESSAGE);
    }

    @Test
    void deserializeSucceedWhenValidMessage() {
        KeyChannelMessage keyChannelMessage = KeyChannelMessage.parse(VALID_CHANNEL_MESSAGE);

        assertThat(keyChannelMessage.eventBusId()).isEqualTo(EVENT_BUS_ID);
        assertThat(keyChannelMessage.routingKey()).isEqualTo(ROUTING_KEY.asString());
        assertThat(keyChannelMessage.eventAsJson()).isEqualTo(EVENT_AS_JSON);
    }

    @Test
    void deserializeFailWhenInvalidMessage() {
        assertThatThrownBy(() -> KeyChannelMessage.parse(INVALID_CHANNEL_MESSAGE))
            .hasMessage("Can not parse the Redis event bus keys channel message");
    }
}
