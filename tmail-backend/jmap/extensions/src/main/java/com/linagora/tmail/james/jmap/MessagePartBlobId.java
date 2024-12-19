package com.linagora.tmail.james.jmap;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.james.jmap.mail.BlobId;
import org.apache.james.jmap.mail.PartId;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.base.Preconditions;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public final class MessagePartBlobId {
    private static final Pattern MESSAGE_PART_BLOB_ID_PATTERN =
        Pattern.compile("^[^_]+_\\d+(_\\d+)*$");

    private final String value;
    private final String messageId;
    private final List<Long> partIds;

    public MessagePartBlobId(String value) {
        Preconditions.checkNotNull(value);
        if (!MESSAGE_PART_BLOB_ID_PATTERN.asMatchPredicate().test(value)) {
            throw new IllegalArgumentException(
                "Invalid BlobId '%s'. Blob id needs to match this format: {message_id}_{partId1}_{partId2}_..."
                    .formatted(value));
        }

        this.value = value;

        String[] parts = value.split("_");
        this.messageId = parts[0];
        this.partIds = Arrays.stream(parts)
                .skip(1)
                .map(Long::parseLong)
                .toList();
    }

    public String getMessageId() {
        return messageId;
    }

    public List<Long> getPartIds() {
        return partIds;
    }

    public String getValue() {
        return value;
    }

    public Tuple2<MessageId, List<BlobId>> asMessageAndPartIds(MessageId.Factory messageIdFactory) {
        return Tuples.of(messageIdFactory.fromString(messageId), toBlobIds(messageIdFactory));
    }

    private List<BlobId> toBlobIds(MessageId.Factory messageIdFactory) {
        BlobId messageIdObject = BlobId.of(messageIdFactory.fromString(messageId)).get();
        return partIds.stream()
            .map(Object::toString)
            .map(partId -> BlobId.of(messageIdObject, PartId.parse(partId).get()).get())
            .toList();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (MessagePartBlobId) obj;
        return Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "MessagePartBlobId[" +
               "value=" + value + ']';
    }

}
