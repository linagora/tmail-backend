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

package com.linagora.tmail.vault.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.util.ReactorUtils.publishIfPresent;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.MESSAGE_ID;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.OWNER;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.PAYLOAD;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.DeletedMessageMetadataTable.TABLE;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.linagora.tmail.vault.metadata.TmailMetadataSerializer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TmailMetadataDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement removeStatement;
    private final PreparedStatement removeAllStatement;
    private final PreparedStatement readStatement;
    private final PreparedStatement readMessageIdStatement;
    private final MessageId.Factory messageIdFactory;
    private final TmailMetadataSerializer metadataSerializer;

    @Inject
    public TmailMetadataDAO(CqlSession session, MessageId.Factory messageIdFactory, TmailMetadataSerializer metadataSerializer) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAdd(session);
        this.removeStatement = prepareRemove(session);
        this.removeAllStatement = prepareRemoveAll(session);
        this.readStatement = prepareRead(session, PAYLOAD);
        this.readMessageIdStatement = prepareRead(session, MESSAGE_ID);
        this.messageIdFactory = messageIdFactory;
        this.metadataSerializer = metadataSerializer;
    }

    private PreparedStatement prepareRead(CqlSession session, String fieldName) {
        return session.prepare(selectFrom(TABLE)
            .columns(fieldName)
            .whereColumn(BUCKET_NAME).isEqualTo(bindMarker(BUCKET_NAME))
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .build());
    }

    private PreparedStatement prepareAdd(CqlSession session) {
        return session.prepare(insertInto(TABLE)
            .value(BUCKET_NAME, bindMarker(BUCKET_NAME))
            .value(OWNER, bindMarker(OWNER))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(PAYLOAD, bindMarker(PAYLOAD))
            .build());
    }

    private PreparedStatement prepareRemove(CqlSession session) {
        return session.prepare(deleteFrom(TABLE)
            .whereColumn(BUCKET_NAME).isEqualTo(bindMarker(BUCKET_NAME))
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());
    }

    private PreparedStatement prepareRemoveAll(CqlSession session) {
        return session.prepare(deleteFrom(TABLE)
            .whereColumn(BUCKET_NAME).isEqualTo(bindMarker(BUCKET_NAME))
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .build());
    }

    Mono<Void> store(DeletedMessageWithStorageInformation metadata) {
        return Mono.just(metadata)
            .map(metadataSerializer::serialize)
            .flatMap(payload -> cassandraAsyncExecutor.executeVoid(addStatement.bind()
                .setString(BUCKET_NAME, metadata.getStorageInformation().getBucketName().asString())
                .setString(OWNER, metadata.getDeletedMessage().getOwner().asString())
                .setString(MESSAGE_ID, metadata.getDeletedMessage().getMessageId().serialize())
                .setString(PAYLOAD, payload)));
    }

    Flux<DeletedMessageWithStorageInformation> retrieveMetadata(BucketName bucketName, Username username) {
        return cassandraAsyncExecutor.executeRows(
            readStatement.bind()
                .setString(BUCKET_NAME, bucketName.asString())
                .setString(OWNER, username.asString()))
            .map(row -> row.getString(PAYLOAD))
            .map(metadataSerializer::deserialize)
            .handle(publishIfPresent());
    }

    Flux<MessageId> retrieveMessageIds(BucketName bucketName, Username username) {
        return cassandraAsyncExecutor.executeRows(
            readMessageIdStatement.bind()
                .setString(BUCKET_NAME, bucketName.asString())
                .setString(OWNER, username.asString()))
            .map(row -> row.getString(MESSAGE_ID))
            .map(messageIdFactory::fromString);
    }

    Mono<Void> deleteMessage(BucketName bucketName, Username username, MessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(removeStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString())
            .setString(OWNER, username.asString())
            .setString(MESSAGE_ID, messageId.serialize()));
    }

    Mono<Void> deleteInBucket(BucketName bucketName, Username username) {
        return cassandraAsyncExecutor.executeVoid(removeAllStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString())
            .setString(OWNER, username.asString()));
    }
}
