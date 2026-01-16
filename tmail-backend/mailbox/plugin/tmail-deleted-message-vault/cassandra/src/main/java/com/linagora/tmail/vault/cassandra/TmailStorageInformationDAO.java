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
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.StorageInformationTable.BLOB_ID;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.StorageInformationTable.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.StorageInformationTable.MESSAGE_ID;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.StorageInformationTable.OWNER;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.StorageInformationTable.TABLE;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.metadata.StorageInformation;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Mono;

public class TmailStorageInformationDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement removeStatement;
    private final PreparedStatement readStatement;

    @Inject
    TmailStorageInformationDAO(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAdd(session);
        this.removeStatement = prepareRemove(session);
        this.readStatement = prepareRead(session);
    }

    private PreparedStatement prepareRead(CqlSession session) {
        return session.prepare(selectFrom(TABLE)
            .columns(BUCKET_NAME, BLOB_ID)
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());
    }

    private PreparedStatement prepareRemove(CqlSession session) {
        return session.prepare(deleteFrom(TABLE)
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());
    }

    private PreparedStatement prepareAdd(CqlSession session) {
        return session.prepare(insertInto(TABLE)
            .value(OWNER, bindMarker(OWNER))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(BUCKET_NAME, bindMarker(BUCKET_NAME))
            .value(BLOB_ID, bindMarker(BLOB_ID))
            .build());
    }

    Mono<Void> referenceStorageInformation(Username username, MessageId messageId, StorageInformation storageInformation) {
        return cassandraAsyncExecutor.executeVoid(addStatement.bind()
            .setString(OWNER, username.asString())
            .setString(MESSAGE_ID, messageId.serialize())
            .setString(BUCKET_NAME, storageInformation.getBucketName().asString())
            .setString(BLOB_ID, storageInformation.getBlobId().asString()));
    }

    Mono<Void> deleteStorageInformation(Username username, MessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(removeStatement.bind()
            .setString(OWNER, username.asString())
            .setString(MESSAGE_ID, messageId.serialize()));
    }

    Mono<StorageInformation> retrieveStorageInformation(Username username, MessageId messageId) {
        return cassandraAsyncExecutor.executeSingleRow(readStatement.bind()
            .setString(OWNER, username.asString())
            .setString(MESSAGE_ID, messageId.serialize()))
            .map(row -> StorageInformation.builder()
                    .bucketName(BucketName.of(row.getString(BUCKET_NAME)))
                    .blobId(new PlainBlobId(row.getString(BLOB_ID))));
    }
}
