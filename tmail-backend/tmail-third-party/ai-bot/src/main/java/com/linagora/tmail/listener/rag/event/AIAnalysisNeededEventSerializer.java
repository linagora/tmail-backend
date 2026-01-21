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

package com.linagora.tmail.listener.rag.event;

import java.util.Collection;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventSerializer;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

public class AIAnalysisNeededEventSerializer implements EventSerializer {
    private record AIAnalysisNeededDTO(String eventId, String username, String mailboxId, String messageId) {

    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MailboxId.Factory mailboxIdFactory;
    private final MessageId.Factory messageIdFactory;

    @Inject
    public AIAnalysisNeededEventSerializer(MailboxId.Factory mailboxIdFactory, MessageId.Factory messageIdFactory) {
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
    }

    @Override
    public String toJson(Event event) {
        if (!(event instanceof AIAnalysisNeeded aiAnalysisNeeded)) {
            throw new IllegalArgumentException("Unsupported event type: " + event.getClass().getName());
        }

        try {
            AIAnalysisNeededDTO dto = new AIAnalysisNeededDTO(aiAnalysisNeeded.getEventId().getId().toString(),
                aiAnalysisNeeded.getUsername().asString(),
                aiAnalysisNeeded.mailboxId().serialize(),
                aiAnalysisNeeded.messageId().serialize());
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJson(Collection<Event> events) {
        if (events.size() != 1) {
            throw new IllegalArgumentException("Not supported for multiple events, please serialize separately");
        }
        return toJson(events.iterator().next());
    }

    @Override
    public Event asEvent(String serialized) {
        try {
            AIAnalysisNeededDTO dto = objectMapper.readValue(serialized, AIAnalysisNeededDTO.class);
            return new AIAnalysisNeeded(Event.EventId.of(dto.eventId()), Username.of(dto.username()),
                mailboxIdFactory.fromString(dto.mailboxId()), messageIdFactory.fromString(dto.messageId()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Event> asEvents(String serialized) {
        return ImmutableList.of(asEvent(serialized));
    }
}
