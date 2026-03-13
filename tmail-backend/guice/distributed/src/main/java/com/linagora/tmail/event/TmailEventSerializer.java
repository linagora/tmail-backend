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

import java.util.Collection;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventSerializer;

import scala.jdk.javaapi.OptionConverters;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.blob.secondaryblobstore.FailedBlobEvents;
import com.linagora.tmail.blob.secondaryblobstore.ObjectStorageIdentity;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.TmailContactUserAddedEvent;
import org.apache.james.jmap.mail.Keyword;

import com.linagora.tmail.james.jmap.label.LabelCreated;
import com.linagora.tmail.james.jmap.label.LabelDestroyed;
import com.linagora.tmail.james.jmap.label.LabelUpdated;
import com.linagora.tmail.james.jmap.model.Color;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelId;

public class TmailEventSerializer implements EventSerializer {

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = BlobAdditionDTO.class),
        @JsonSubTypes.Type(value = BlobsDeletionDTO.class),
        @JsonSubTypes.Type(value = BucketDeletionDTO.class),
        @JsonSubTypes.Type(value = TmailContactUserAddedEventDTO.class),
        @JsonSubTypes.Type(value = LabelCreatedDTO.class),
        @JsonSubTypes.Type(value = LabelUpdatedDTO.class),
        @JsonSubTypes.Type(value = LabelDestroyedDTO.class)
    })
    interface EventDTO {
    }

    record BlobAdditionDTO(String eventId, String username, String bucketName, String blobId, String failedObjectStorage) implements EventDTO {
    }

    record BlobsDeletionDTO(String eventId, String username, String bucketName, Collection<String> blobIds, String failedObjectStorage) implements EventDTO {
    }

    record BucketDeletionDTO(String eventId, String username, String bucketName, String failedObjectStorage) implements EventDTO {
    }

    record TmailContactUserAddedEventDTO(String eventId, String username, String contactAddress, String contactFirstname, String contactSurname) implements EventDTO {
    }

    record LabelCreatedDTO(String eventId, String username, String keyword, String displayName, String color, String description) implements EventDTO {
    }

    record LabelUpdatedDTO(String eventId, String username, String keyword, String displayName, String color, String description) implements EventDTO {
    }

    record LabelDestroyedDTO(String eventId, String username, String keyword) implements EventDTO {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlobId.Factory blobIdFactory;

    @Inject
    public TmailEventSerializer(BlobId.Factory blobIdFactory) {
        this.blobIdFactory = blobIdFactory;
    }

    @Override
    public String toJson(Event event) {
        try {
            EventDTO eventDTO = toDTO(event);
            return objectMapper.writeValueAsString(eventDTO);
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
            EventDTO eventDTO = objectMapper.readValue(serialized, EventDTO.class);
            return fromDTO(eventDTO);
        } catch (JsonProcessingException | AddressException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Event> asEvents(String serialized) {
        return ImmutableList.of(asEvent(serialized));
    }


    private Label dtoToLabel(String keyword, String displayName, String color, String description) {
        return new Label(
            LabelId.fromKeyword(keyword),
            new DisplayName(displayName),
            keyword,
            scala.Option.apply(color != null ? new Color(color) : null),
            scala.Option.apply(description));
    }

    private EventDTO toDTO(Event event) {
        return switch (event) {
            case FailedBlobEvents.BlobAddition e -> new BlobAdditionDTO(e.getEventId().getId().toString(), FailedBlobEvents.BlobEvent.USERNAME.asString(),
                e.bucketName().asString(), e.blobId().asString(), e.getFailedObjectStorage().name());
            case FailedBlobEvents.BlobsDeletion e -> new BlobsDeletionDTO(e.getEventId().getId().toString(), FailedBlobEvents.BlobEvent.USERNAME.asString(),
                e.bucketName().asString(), e.blobIds().stream().map(BlobId::asString).toList(),
                e.getFailedObjectStorage().name());
            case FailedBlobEvents.BucketDeletion e -> new BucketDeletionDTO(e.getEventId().getId().toString(), FailedBlobEvents.BlobEvent.USERNAME.asString(),
                e.bucketName().asString(), e.getFailedObjectStorage().name());
            case TmailContactUserAddedEvent e -> new TmailContactUserAddedEventDTO(e.getEventId().getId().toString(), e.username().asString(),
                e.contact().address().asString(), e.contact().firstname(), e.contact().surname());
            case LabelCreated e -> new LabelCreatedDTO(e.getEventId().getId().toString(), e.username().asString(),
                e.label().keyword(), e.label().displayName().value(),
                OptionConverters.toJava(e.label().color()).map(Color::value).orElse(null),
                OptionConverters.toJava(e.label().description()).orElse(null));
            case LabelUpdated e -> new LabelUpdatedDTO(e.getEventId().getId().toString(), e.username().asString(),
                e.updatedLabel().keyword(), e.updatedLabel().displayName().value(),
                OptionConverters.toJava(e.updatedLabel().color()).map(Color::value).orElse(null),
                OptionConverters.toJava(e.updatedLabel().description()).orElse(null));
            case LabelDestroyed e -> new LabelDestroyedDTO(e.getEventId().getId().toString(), e.username().asString(),
                e.labelId().toKeyword());
            default -> throw new IllegalStateException("Unexpected value: " + event);
        };
    }

    private Event fromDTO(EventDTO eventDTO) throws AddressException {
        return switch (eventDTO) {
            case BlobAdditionDTO dto -> new FailedBlobEvents.BlobAddition(Event.EventId.of(dto.eventId()),
                BucketName.of(dto.bucketName()), blobIdFactory.parse(dto.blobId()), ObjectStorageIdentity.valueOf(dto.failedObjectStorage()));
            case BlobsDeletionDTO dto -> new FailedBlobEvents.BlobsDeletion(Event.EventId.of(dto.eventId()),
                BucketName.of(dto.bucketName()), dto.blobIds().stream().map(blobId -> blobIdFactory.parse(blobId)).toList(),
                ObjectStorageIdentity.valueOf(dto.failedObjectStorage()));
            case BucketDeletionDTO dto -> new FailedBlobEvents.BucketDeletion(Event.EventId.of(dto.eventId()),
                BucketName.of(dto.bucketName()), ObjectStorageIdentity.valueOf(dto.failedObjectStorage()));
            case TmailContactUserAddedEventDTO dto -> new TmailContactUserAddedEvent(Event.EventId.of(dto.eventId()),
                Username.of(dto.username()), new ContactFields(new MailAddress(dto.contactAddress()), dto.contactFirstname(), dto.contactSurname()));
            case LabelCreatedDTO dto -> new LabelCreated(Event.EventId.of(dto.eventId()),
                Username.of(dto.username()), dtoToLabel(dto.keyword(), dto.displayName(), dto.color(), dto.description()));
            case LabelUpdatedDTO dto -> new LabelUpdated(Event.EventId.of(dto.eventId()),
                Username.of(dto.username()), dtoToLabel(dto.keyword(), dto.displayName(), dto.color(), dto.description()));
            case LabelDestroyedDTO dto -> new LabelDestroyed(Event.EventId.of(dto.eventId()), Username.of(dto.username()), LabelId.fromKeyword(dto.keyword));
            default -> throw new IllegalStateException("Unexpected value: " + eventDTO);
        };
    }
}
