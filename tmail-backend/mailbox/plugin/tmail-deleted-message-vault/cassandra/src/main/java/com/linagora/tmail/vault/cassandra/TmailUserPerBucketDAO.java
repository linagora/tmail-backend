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
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.UserPerBucketTable.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.UserPerBucketTable.TABLE;
import static org.apache.james.vault.metadata.DeletedMessageMetadataDataDefinition.UserPerBucketTable.USER;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TmailUserPerBucketDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement removeStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement listBucketsStatement;

    @Inject
    public TmailUserPerBucketDAO(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAddUser(session);
        this.removeStatement = prepareRemoveBucket(session);
        this.listStatement = prepareListUser(session);
        this.listBucketsStatement = prepareListBuckets(session);
    }

    private PreparedStatement prepareAddUser(CqlSession session) {
        return session.prepare(insertInto(TABLE)
            .value(BUCKET_NAME, bindMarker(BUCKET_NAME))
            .value(USER, bindMarker(USER))
            .build());
    }

    private PreparedStatement prepareRemoveBucket(CqlSession session) {
        return session.prepare(deleteFrom(TABLE)
            .whereColumn(BUCKET_NAME).isEqualTo(bindMarker(BUCKET_NAME))
            .build());
    }

    private PreparedStatement prepareListUser(CqlSession session) {
        return session.prepare(selectFrom(TABLE)
            .column(USER)
            .whereColumn(BUCKET_NAME).isEqualTo(bindMarker(BUCKET_NAME))
            .build());
    }

    private PreparedStatement prepareListBuckets(CqlSession session) {
        return session.prepare(selectFrom(TABLE)
            .column(BUCKET_NAME)
            .perPartitionLimit(1)
            .build());
    }

    public Flux<Username> retrieveUsers(BucketName bucketName) {
        return cassandraAsyncExecutor.executeRows(listStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString()))
            .map(row -> row.getString(USER))
            .map(Username::of);
    }

    public Flux<BucketName> retrieveBuckets() {
        return cassandraAsyncExecutor.executeRows(listBucketsStatement.bind())
            .map(row -> row.getString(BUCKET_NAME))
            .map(BucketName::of);
    }

    public Mono<Void> addUser(BucketName bucketName, Username username) {
        return cassandraAsyncExecutor.executeVoid(addStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString())
            .setString(USER, username.asString()));
    }

    public Mono<Void> deleteBucket(BucketName bucketName) {
        return cassandraAsyncExecutor.executeVoid(removeStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString()));
    }
}
