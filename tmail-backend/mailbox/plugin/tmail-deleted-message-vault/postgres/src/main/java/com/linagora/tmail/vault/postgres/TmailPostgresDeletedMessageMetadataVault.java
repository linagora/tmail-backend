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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.vault.postgres;

import static org.apache.james.util.ReactorUtils.publishIfPresent;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.BLOB_ID;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.BUCKET_NAME;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.MESSAGE_ID;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.METADATA;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.OWNER;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.TABLE_NAME;
import static org.jooq.JSONB.jsonb;

import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.apache.james.vault.metadata.StorageInformation;
import org.jooq.Record;
import org.reactivestreams.Publisher;

import com.linagora.tmail.vault.blob.BlobIdTimeGenerator;
import com.linagora.tmail.vault.metadata.TmailMetadataSerializer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TmailPostgresDeletedMessageMetadataVault implements DeletedMessageMetadataVault {
    private final PostgresExecutor postgresExecutor;
    private final TmailMetadataSerializer metadataSerializer;
    private final BlobIdTimeGenerator blobIdTimeGenerator;

    @Inject
    public TmailPostgresDeletedMessageMetadataVault(PostgresExecutor postgresExecutor,
                                                    TmailMetadataSerializer metadataSerializer,
                                                    BlobIdTimeGenerator blobIdTimeGenerator) {
        this.postgresExecutor = postgresExecutor;
        this.metadataSerializer = metadataSerializer;
        this.blobIdTimeGenerator = blobIdTimeGenerator;
    }

    @Override
    public Publisher<Void> store(DeletedMessageWithStorageInformation deletedMessage) {
        return postgresExecutor.executeVoid(context -> Mono.from(context.insertInto(TABLE_NAME)
            .set(OWNER, deletedMessage.getDeletedMessage().getOwner().asString())
            .set(MESSAGE_ID, deletedMessage.getDeletedMessage().getMessageId().serialize())
            .set(BUCKET_NAME, deletedMessage.getStorageInformation().getBucketName().asString())
            .set(BLOB_ID, deletedMessage.getStorageInformation().getBlobId().asString())
            .set(METADATA, jsonb(metadataSerializer.serialize(deletedMessage)))));
    }

    @Override
    public Publisher<Void> removeMetadataRelatedToBucket(BucketName bucketName) {
        return postgresExecutor.executeVoid(context -> Mono.from(context.deleteFrom(TABLE_NAME)
            .where(BUCKET_NAME.eq(bucketName.asString()))));
    }

    @Override
    public Publisher<Void> remove(BucketName bucketName, Username username, MessageId messageId) {
        return postgresExecutor.executeVoid(context -> Mono.from(context.deleteFrom(TABLE_NAME)
            .where(BUCKET_NAME.eq(bucketName.asString()),
                OWNER.eq(username.asString()),
                MESSAGE_ID.eq(messageId.serialize()))));
    }

    @Override
    public Publisher<StorageInformation> retrieveStorageInformation(Username username, MessageId messageId) {
        return postgresExecutor.executeRow(context -> Mono.from(context.select(BUCKET_NAME, BLOB_ID)
            .from(TABLE_NAME)
            .where(OWNER.eq(username.asString()),
                MESSAGE_ID.eq(messageId.serialize()))))
            .map(toStorageInformation());
    }

    private Function<Record, StorageInformation> toStorageInformation() {
        return record -> StorageInformation.builder()
            .bucketName(BucketName.of(record.get(BUCKET_NAME)))
            .blobId(blobIdTimeGenerator.toDeletedMessageBlobId(record.get(BLOB_ID)));
    }

    @Override
    public Publisher<DeletedMessageWithStorageInformation> listMessages(BucketName bucketName, Username username) {
        return postgresExecutor.executeRows(context -> Flux.from(context.select(METADATA)
            .from(TABLE_NAME)
            .where(BUCKET_NAME.eq(bucketName.asString()),
                OWNER.eq(username.asString()))))
            .map(record -> metadataSerializer.deserialize(record.get(METADATA).data()))
            .handle(publishIfPresent());
    }

    @Override
    public Publisher<BucketName> listRelatedBuckets() {
        return postgresExecutor.executeRows(context -> Flux.from(context.selectDistinct(BUCKET_NAME)
            .from(TABLE_NAME)))
            .map(record -> BucketName.of(record.get(BUCKET_NAME)));
    }
}
