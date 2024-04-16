package com.linagora.tmail.encrypted.postgres;

import static com.linagora.tmail.encrypted.postgres.table.PostgresEncryptedEmailStoreModule.PostgresEncryptedEmailStoreTable.ENCRYPTED_ATTACHMENT_METADATA;
import static com.linagora.tmail.encrypted.postgres.table.PostgresEncryptedEmailStoreModule.PostgresEncryptedEmailStoreTable.ENCRYPTED_HTML;
import static com.linagora.tmail.encrypted.postgres.table.PostgresEncryptedEmailStoreModule.PostgresEncryptedEmailStoreTable.ENCRYPTED_PREVIEW;
import static com.linagora.tmail.encrypted.postgres.table.PostgresEncryptedEmailStoreModule.PostgresEncryptedEmailStoreTable.HAS_ATTACHMENT;
import static com.linagora.tmail.encrypted.postgres.table.PostgresEncryptedEmailStoreModule.PostgresEncryptedEmailStoreTable.MESSAGE_ID;
import static com.linagora.tmail.encrypted.postgres.table.PostgresEncryptedEmailStoreModule.PostgresEncryptedEmailStoreTable.POSITION_BLOB_ID_MAPPING;
import static com.linagora.tmail.encrypted.postgres.table.PostgresEncryptedEmailStoreModule.PostgresEncryptedEmailStoreTable.TABLE_NAME;

import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.postgres.extensions.types.Hstore;

import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.encrypted.EncryptedAttachmentMetadata;
import com.linagora.tmail.encrypted.EncryptedEmailContent;
import com.linagora.tmail.encrypted.EncryptedEmailDetailedView;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.compat.java8.OptionConverters;

public class PostgresEncryptedEmailStoreDAO {
    private static final String EMPTY_ENCRYPTED_ATTACHMENT_METADATA = null;

    private final PostgresExecutor postgresExecutor;
    private final BlobId.Factory blobIdFactory;

    @Inject
    public PostgresEncryptedEmailStoreDAO(PostgresExecutor postgresExecutor, BlobId.Factory blobIdFactory) {
        this.postgresExecutor = postgresExecutor;
        this.blobIdFactory = blobIdFactory;
    }

    public Mono<Void> insert(PostgresMessageId messageId, EncryptedEmailContent encryptedEmailContent, Map<Integer, BlobId> positionBlobIdMapping) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(MESSAGE_ID, messageId.asUuid())
            .set(ENCRYPTED_PREVIEW, encryptedEmailContent.encryptedPreview())
            .set(ENCRYPTED_HTML, encryptedEmailContent.encryptedHtml())
            .set(HAS_ATTACHMENT, encryptedEmailContent.hasAttachment())
            .set(ENCRYPTED_ATTACHMENT_METADATA, OptionConverters.toJava(encryptedEmailContent.encryptedAttachmentMetadata())
                .orElse(EMPTY_ENCRYPTED_ATTACHMENT_METADATA))
            .set(POSITION_BLOB_ID_MAPPING, toHstore(positionBlobIdMapping))));
    }

    public Mono<EncryptedEmailDetailedView> getEmail(PostgresMessageId messageId) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.selectFrom(TABLE_NAME)
            .where(MESSAGE_ID.eq(messageId.asUuid()))))
            .map(this::readRecord);
    }

    public Mono<BlobId> getBlobId(PostgresMessageId messageId, Integer position) {
        return postgresExecutor.executeRow(dslContext ->
            Mono.from(dslContext.select(DSL.field(POSITION_BLOB_ID_MAPPING.getName() + "[?]", position.toString()))
                .from(TABLE_NAME)
                .where(MESSAGE_ID.eq(messageId.asUuid()))))
            .flatMap(record -> Optional.ofNullable(record.get(0, String.class)).map(Mono::just).orElse(Mono.empty()))
            .map(blobIdFactory::from);
    }

    public Flux<BlobId> getBlobIds(PostgresMessageId messageId) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(POSITION_BLOB_ID_MAPPING)
            .from(TABLE_NAME)
            .where(MESSAGE_ID.eq(messageId.asUuid()))))
            .map(record -> record.get(POSITION_BLOB_ID_MAPPING, Hstore.class).data())
            .flatMapMany(positionBlobIdMapping -> Flux.fromIterable(positionBlobIdMapping.values()))
            .map(blobIdFactory::from);
    }

    public Flux<BlobId> getAllBlobIds() {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(POSITION_BLOB_ID_MAPPING)
                .from(TABLE_NAME)))
            .map(record -> record.get(POSITION_BLOB_ID_MAPPING, Hstore.class).data())
            .flatMap(positionBlobIdMapping -> Flux.fromIterable(positionBlobIdMapping.values()))
            .map(blobIdFactory::from);
    }

    public Mono<Void> delete(PostgresMessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(MESSAGE_ID.eq(messageId.asUuid()))));
    }

    private Hstore toHstore(Map<Integer, BlobId> positionBlobIdMapping) {
        return Hstore.hstore(positionBlobIdMapping.entrySet()
            .stream()
            .collect(ImmutableMap.toImmutableMap(entry -> entry.getKey().toString(), entry -> entry.getValue().asString())));
    }

    private EncryptedEmailDetailedView readRecord(Record record) {
        return new EncryptedEmailDetailedView(PostgresMessageId.Factory.of(record.get(MESSAGE_ID)),
            record.get(ENCRYPTED_PREVIEW),
            record.get(ENCRYPTED_HTML),
            record.get(HAS_ATTACHMENT),
            OptionConverters.toScala(Optional.ofNullable(record.get(ENCRYPTED_ATTACHMENT_METADATA))
                .map(EncryptedAttachmentMetadata::new)));
    }
}
