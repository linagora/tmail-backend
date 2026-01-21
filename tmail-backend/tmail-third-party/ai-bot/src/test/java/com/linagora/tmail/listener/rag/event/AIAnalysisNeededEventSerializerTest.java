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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.junit.jupiter.api.Test;

public class AIAnalysisNeededEventSerializerTest {
    private final AIAnalysisNeededEventSerializer serializer =
        new AIAnalysisNeededEventSerializer(new InMemoryId.Factory(), new InMemoryMessageId.Factory());

    @Test
    void toJsonShouldRoundTrip() {
        Event.EventId eventId = Event.EventId.random();
        Username username = Username.of("bob@example.com");
        AIAnalysisNeeded event = new AIAnalysisNeeded(eventId, username, InMemoryId.of(42), InMemoryMessageId.of(100));

        String serialized = serializer.toJson(event);
        Event deserialized = serializer.asEvent(serialized);

        assertThat(deserialized).isInstanceOf(AIAnalysisNeeded.class);
        AIAnalysisNeeded aiAnalysisNeeded = (AIAnalysisNeeded) deserialized;
        assertThat(aiAnalysisNeeded.getEventId()).isEqualTo(eventId);
        assertThat(aiAnalysisNeeded.getUsername()).isEqualTo(username);
        assertThat(aiAnalysisNeeded.mailboxId()).isEqualTo(InMemoryId.of(42));
        assertThat(aiAnalysisNeeded.messageId()).isEqualTo(InMemoryMessageId.of(100));
    }
}
