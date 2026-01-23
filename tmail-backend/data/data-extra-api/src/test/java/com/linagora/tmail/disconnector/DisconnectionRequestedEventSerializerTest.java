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

package com.linagora.tmail.disconnector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.junit.jupiter.api.Test;

class DisconnectionRequestedEventSerializerTest {
    private final DisconnectionRequestedEventSerializer testee = new DisconnectionRequestedEventSerializer();

    @Test
    void roundTripShouldPreserveEventIdAndUsernames() {
        Set<Username> usernames = Set.of(Username.of("bob@domain.tld"), Username.of("alice@domain.tld"));
        DisconnectionRequested event = new DisconnectionRequested(Event.EventId.random(), usernames);

        Event deserialized = testee.asEvent(testee.toJson(event));

        DisconnectionRequested deserializedDisconnectionRequested = (DisconnectionRequested) deserialized;
        assertThat(deserializedDisconnectionRequested.getEventId()).isEqualTo(event.getEventId());
        assertThat(deserializedDisconnectionRequested.usernames()).isEqualTo(usernames);
        assertThat(deserializedDisconnectionRequested.targetsAllUsers()).isFalse();
    }

    @Test
    void roundTripShouldPreserveEmptyUsernamesToRepresentAllUsers() {
        DisconnectionRequested event = new DisconnectionRequested(Event.EventId.random(), Set.of());

        Event deserialized = testee.asEvent(testee.toJson(event));

        DisconnectionRequested deserializedDisconnectionRequested = (DisconnectionRequested) deserialized;
        assertThat(deserializedDisconnectionRequested.usernames()).isEmpty();
        assertThat(deserializedDisconnectionRequested.targetsAllUsers()).isTrue();
    }

    @Test
    void deserializeShouldTreatEmptyUsernamesAsAllUsers() {
        Event.EventId eventId = Event.EventId.random();

        String json = """
            {
              "eventId": "%s",
              "usernames": []
            }
            """.formatted(eventId.getId());

        Event deserialized = testee.asEvent(json);

        DisconnectionRequested deserializedDisconnectionRequested = (DisconnectionRequested) deserialized;
        assertThat(deserializedDisconnectionRequested.usernames()).isEmpty();
        assertThat(deserializedDisconnectionRequested.targetsAllUsers()).isTrue();
    }

    @Test
    void serializeAllUserCasesShouldReturnEmptyUsernames() {
        Event.EventId eventId = Event.EventId.random();
        DisconnectionRequested event = new DisconnectionRequested(eventId, Set.of());

        assertThat(testee.toJson(event)).isEqualTo("""
            {"eventId":"%s","usernames":[]}""".formatted(eventId.getId()));
    }
}
