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

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.events.DeserializationResult;
import org.apache.james.events.Event;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.SerializationResult;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    public SerializationResult toJson(Event event) {
        if (!(event instanceof AIAnalysisNeeded aiAnalysisNeeded)) {
            return new SerializationResult.Failure("Unsupported event type: " + event.getClass().getName());
        }

        try {
            AIAnalysisNeededDTO dto = new AIAnalysisNeededDTO(aiAnalysisNeeded.getEventId().getId().toString(),
                aiAnalysisNeeded.getUsername().asString(),
                aiAnalysisNeeded.mailboxId().serialize(),
                aiAnalysisNeeded.messageId().serialize());
            return new SerializationResult.Success(objectMapper.writeValueAsString(dto));
        } catch (JsonProcessingException e) {
            return new SerializationResult.Failure(e.getMessage());
        }
    }

    @Override
    public SerializationResult toJson(Collection<Event> events) {
        if (events.size() != 1) {
            return new SerializationResult.Failure("Not supported for multiple events, please serialize separately");
        }
        return toJson(events.iterator().next());
    }

    @Override
    public DeserializationResult asEvent(String serialized) {
        try {
            AIAnalysisNeededDTO dto = objectMapper.readValue(serialized, AIAnalysisNeededDTO.class);
            return new DeserializationResult.Success(new AIAnalysisNeeded(Event.EventId.of(dto.eventId()), Username.of(dto.username()),
                mailboxIdFactory.fromString(dto.mailboxId()), messageIdFactory.fromString(dto.messageId())));
        } catch (JsonProcessingException e) {
            return new DeserializationResult.Failure(e.getMessage());
        }
    }

    @Override
    public DeserializationResult asEvents(String serialized) {
        return asEvent(serialized);
    }
}
