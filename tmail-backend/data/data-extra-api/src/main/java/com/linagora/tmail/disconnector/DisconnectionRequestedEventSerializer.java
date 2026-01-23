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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

@Singleton
public class DisconnectionRequestedEventSerializer implements EventSerializer {
    record DisconnectionRequestedDTO(String eventId, List<String> usernames) {
    }

    private final ObjectMapper objectMapper;

    @Inject
    public DisconnectionRequestedEventSerializer() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String toJson(Event event) {
        if (!(event instanceof DisconnectionRequested disconnectionRequested)) {
            throw new IllegalArgumentException("Unsupported event: " + event);
        }

        DisconnectionRequestedDTO dto = new DisconnectionRequestedDTO(
            disconnectionRequested.getEventId().getId().toString(),
            disconnectionRequested.usernames().stream().map(Username::asString).toList());

        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJson(Collection<Event> event) {
        if (event.size() != 1) {
            throw new IllegalArgumentException("Not supported for multiple events, please serialize separately");
        }
        return toJson(event.iterator().next());
    }

    @Override
    public Event asEvent(String serialized) {
        try {
            DisconnectionRequestedDTO dto = objectMapper.readValue(serialized, DisconnectionRequestedDTO.class);
            List<String> usernamesAsString = dto.usernames() == null ? ImmutableList.of() : dto.usernames();
            Set<Username> usernames = usernamesAsString.stream()
                .map(Username::of)
                .collect(Collectors.toSet());
            return new DisconnectionRequested(Event.EventId.of(dto.eventId()), usernames);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Event> asEvents(String serialized) {
        return List.of(asEvent(serialized));
    }
}
