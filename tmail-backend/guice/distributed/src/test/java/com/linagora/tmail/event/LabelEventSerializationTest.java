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

import java.time.Instant;

import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.label.LabelCreated;
import com.linagora.tmail.james.jmap.label.LabelDestroyed;
import com.linagora.tmail.james.jmap.label.LabelUpdated;
import com.linagora.tmail.james.jmap.model.Color;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelId;

class LabelEventSerializationTest {

    static final GenerationAwareBlobId.Factory BLOB_ID_FACTORY = new GenerationAwareBlobId.Factory(
        new UpdatableTickingClock(Instant.parse("2021-08-19T10:15:30.00Z")),
        new PlainBlobId.Factory(),
        GenerationAwareBlobId.Configuration.DEFAULT);

    static final TmailEventSerializer SERIALIZER = new TmailEventSerializer(BLOB_ID_FACTORY);

    static final Event.EventId EVENT_ID = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    static final Username ALICE = Username.of("alice@domain.com");
    static final String KEYWORD ="mylabel";
    static final LabelId LABEL_ID = LabelId.fromKeyword(KEYWORD);

    static final Label LABEL_WITH_COLOR = new Label(
        LABEL_ID,
        new DisplayName("Work"),
        KEYWORD,
        scala.Option.apply(new Color("#FF0000")),
        scala.Option.apply(null));

    static final Label LABEL_WITH_COLOR_AND_DESCRIPTION = new Label(
        LABEL_ID,
        new DisplayName("Work"),
        KEYWORD,
        scala.Option.apply(new Color("#FF0000")),
        scala.Option.apply("Important label"));

    static final Label LABEL_MINIMAL = new Label(
        LABEL_ID,
        new DisplayName("Work"),
        KEYWORD,
        scala.Option.apply(null),
        scala.Option.apply(null));

    static final String LABEL_CREATED_JSON = "{" +
        "\"type\":\"TmailEventSerializer$LabelCreatedDTO\"," +
        "\"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "\"username\":\"alice@domain.com\"," +
        "\"keyword\":\"mylabel\"," +
        "\"displayName\":\"Work\"," +
        "\"color\":\"#FF0000\"," +
        "\"description\":null" +
        "}";

    static final String LABEL_CREATED_WITH_DESCRIPTION_JSON = "{" +
        "\"type\":\"TmailEventSerializer$LabelCreatedDTO\"," +
        "\"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "\"username\":\"alice@domain.com\"," +
        "\"keyword\":\"mylabel\"," +
        "\"displayName\":\"Work\"," +
        "\"color\":\"#FF0000\"," +
        "\"description\":\"Important label\"" +
        "}";

    static final String LABEL_CREATED_MINIMAL_JSON = "{" +
        "\"type\":\"TmailEventSerializer$LabelCreatedDTO\"," +
        "\"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "\"username\":\"alice@domain.com\"," +
        "\"keyword\":\"mylabel\"," +
        "\"displayName\":\"Work\"," +
        "\"color\":null," +
        "\"description\":null" +
        "}";

    static final String LABEL_UPDATED_JSON = "{" +
        "\"type\":\"TmailEventSerializer$LabelUpdatedDTO\"," +
        "\"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "\"username\":\"alice@domain.com\"," +
        "\"keyword\":\"mylabel\"," +
        "\"displayName\":\"Work\"," +
        "\"color\":\"#FF0000\"," +
        "\"description\":null" +
        "}";

    static final String LABEL_DESTROYED_JSON = "{" +
        "\"type\":\"TmailEventSerializer$LabelDestroyedDTO\"," +
        "\"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "\"username\":\"alice@domain.com\"," +
        "\"keyword\":\"mylabel\"" +
        "}";

    @Test
    void labelCreatedShouldBeWellSerialized() {
        assertThat(SERIALIZER.toJson(new LabelCreated(EVENT_ID, ALICE, LABEL_WITH_COLOR)))
            .isEqualTo(LABEL_CREATED_JSON);
    }

    @Test
    void labelCreatedShouldBeWellDeserialized() {
        assertThat(SERIALIZER.asEvent(LABEL_CREATED_JSON))
            .isEqualTo(new LabelCreated(EVENT_ID, ALICE, LABEL_WITH_COLOR));
    }

    @Test
    void labelCreatedWithDescriptionShouldBeWellSerialized() {
        assertThat(SERIALIZER.toJson(new LabelCreated(EVENT_ID, ALICE, LABEL_WITH_COLOR_AND_DESCRIPTION)))
            .isEqualTo(LABEL_CREATED_WITH_DESCRIPTION_JSON);
    }

    @Test
    void labelCreatedWithDescriptionShouldBeWellDeserialized() {
        assertThat(SERIALIZER.asEvent(LABEL_CREATED_WITH_DESCRIPTION_JSON))
            .isEqualTo(new LabelCreated(EVENT_ID, ALICE, LABEL_WITH_COLOR_AND_DESCRIPTION));
    }

    @Test
    void labelCreatedWithoutOptionalFieldsShouldBeWellSerialized() {
        assertThat(SERIALIZER.toJson(new LabelCreated(EVENT_ID, ALICE, LABEL_MINIMAL)))
            .isEqualTo(LABEL_CREATED_MINIMAL_JSON);
    }

    @Test
    void labelCreatedWithoutOptionalFieldsShouldBeWellDeserialized() {
        assertThat(SERIALIZER.asEvent(LABEL_CREATED_MINIMAL_JSON))
            .isEqualTo(new LabelCreated(EVENT_ID, ALICE, LABEL_MINIMAL));
    }

    @Test
    void labelUpdatedShouldBeWellSerialized() {
        assertThat(SERIALIZER.toJson(new LabelUpdated(EVENT_ID, ALICE, LABEL_WITH_COLOR)))
            .isEqualTo(LABEL_UPDATED_JSON);
    }

    @Test
    void labelUpdatedShouldBeWellDeserialized() {
        assertThat(SERIALIZER.asEvent(LABEL_UPDATED_JSON))
            .isEqualTo(new LabelUpdated(EVENT_ID, ALICE, LABEL_WITH_COLOR));
    }

    @Test
    void labelDestroyedShouldBeWellSerialized() {
        assertThat(SERIALIZER.toJson(new LabelDestroyed(EVENT_ID, ALICE, LABEL_ID)))
            .isEqualTo(LABEL_DESTROYED_JSON);
    }

    @Test
    void labelDestroyedShouldBeWellDeserialized() {
        assertThat(SERIALIZER.asEvent(LABEL_DESTROYED_JSON))
            .isEqualTo(new LabelDestroyed(EVENT_ID, ALICE, LABEL_ID));
    }
}
