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

package com.linagora.tmail.james.jmap.projections;

import static com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.DESC;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.KEYWORD;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.MESSAGE_ID;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.RECEIVED_AT;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.TABLE_NAME;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.THREAD_ID;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.USERNAME;

import java.time.Instant;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.projections.EmailQueryViewUtils;
import org.apache.james.jmap.api.projections.EmailQueryViewUtils.EmailEntry;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.util.streams.Limit;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraKeywordEmailQueryView implements KeywordEmailQueryView {
    private static final String LIMIT_MARKER = "LIMIT_BIND_MARKER";
    private static final String AFTER_MARKER = "AFTER_BIND_MARKER";
    private static final String BEFORE_MARKER = "BEFORE_BIND_MARKER";

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement listByKeyword;
    private final PreparedStatement listByKeywordSinceAfter;
    private final PreparedStatement listByKeywordBefore;
    private final PreparedStatement listByKeywordRange;
    private final PreparedStatement insert;
    private final PreparedStatement delete;

    @Inject
    public CassandraKeywordEmailQueryView(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);

        listByKeyword = session.prepare(selectFrom(TABLE_NAME)
            .columns(MESSAGE_ID, RECEIVED_AT, THREAD_ID)
            .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
            .whereColumn(KEYWORD).isEqualTo(bindMarker(KEYWORD))
            .orderBy(RECEIVED_AT, DESC)
            .limit(bindMarker(LIMIT_MARKER))
            .build());

        listByKeywordSinceAfter = session.prepare(selectFrom(TABLE_NAME)
            .columns(MESSAGE_ID, RECEIVED_AT, THREAD_ID)
            .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
            .whereColumn(KEYWORD).isEqualTo(bindMarker(KEYWORD))
            .whereColumn(RECEIVED_AT).isGreaterThanOrEqualTo(bindMarker(AFTER_MARKER))
            .orderBy(RECEIVED_AT, DESC)
            .limit(bindMarker(LIMIT_MARKER))
            .build());

        listByKeywordBefore = session.prepare(selectFrom(TABLE_NAME)
            .columns(MESSAGE_ID, RECEIVED_AT, THREAD_ID)
            .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
            .whereColumn(KEYWORD).isEqualTo(bindMarker(KEYWORD))
            .whereColumn(RECEIVED_AT).isLessThan(bindMarker(BEFORE_MARKER))
            .orderBy(RECEIVED_AT, DESC)
            .limit(bindMarker(LIMIT_MARKER))
            .build());

        listByKeywordRange = session.prepare(selectFrom(TABLE_NAME)
            .columns(MESSAGE_ID, RECEIVED_AT, THREAD_ID)
            .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
            .whereColumn(KEYWORD).isEqualTo(bindMarker(KEYWORD))
            .whereColumn(RECEIVED_AT).isGreaterThanOrEqualTo(bindMarker(AFTER_MARKER))
            .whereColumn(RECEIVED_AT).isLessThan(bindMarker(BEFORE_MARKER))
            .orderBy(RECEIVED_AT, DESC)
            .limit(bindMarker(LIMIT_MARKER))
            .build());

        insert = session.prepare(insertInto(TABLE_NAME)
            .value(USERNAME, bindMarker(USERNAME))
            .value(KEYWORD, bindMarker(KEYWORD))
            .value(RECEIVED_AT, bindMarker(RECEIVED_AT))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(THREAD_ID, bindMarker(THREAD_ID))
            .build());

        delete = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
            .whereColumn(KEYWORD).isEqualTo(bindMarker(KEYWORD))
            .whereColumn(RECEIVED_AT).isEqualTo(bindMarker(RECEIVED_AT))
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());
    }

    @Override
    public Mono<Void> save(Username username, Keyword keyword, Instant receivedAt, MessageId messageId, ThreadId threadId) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;

        return executor.executeVoid(insert.bind()
            .set(USERNAME, username.asString(), TypeCodecs.TEXT)
            .set(KEYWORD, keyword.flagName(), TypeCodecs.TEXT)
            .setInstant(RECEIVED_AT, receivedAt)
            .setUuid(MESSAGE_ID, cassandraMessageId.get())
            .setUuid(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get()));
    }

    @Override
    public Mono<Void> delete(Username username, Keyword keyword, Instant receivedAt, MessageId messageId) {
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;

        return executor.executeVoid(delete.bind()
            .set(USERNAME, username.asString(), TypeCodecs.TEXT)
            .set(KEYWORD, keyword.flagName(), TypeCodecs.TEXT)
            .setInstant(RECEIVED_AT, receivedAt)
            .setUuid(MESSAGE_ID, cassandraMessageId.get()));
    }

    @Override
    public Mono<Void> truncate() {
        return executor.executeVoid(QueryBuilder.truncate(TABLE_NAME)
            .build());
    }

    @Override
    public Flux<MessageId> listMessagesByKeyword(Username username, Keyword keyword, Options options) {
        return EmailQueryViewUtils.QueryViewExtender.of(options.limit(), options.collapseThread())
            .resolve(backendFetchLimit -> executor.executeRows(buildSelectStatement(username, keyword, options, backendFetchLimit))
                .map(asEmailEntry()));
    }

    private Function<Row, EmailEntry> asEmailEntry() {
        return row -> {
            CassandraMessageId messageId = CassandraMessageId.Factory.of(row.getUuid(MESSAGE_ID));
            ThreadId threadId = ThreadId.fromBaseMessageId(CassandraMessageId.Factory.of(row.getUuid(THREAD_ID)));
            return new EmailEntry(messageId, threadId, row.getInstant(RECEIVED_AT));
        };
    }

    private BoundStatement buildSelectStatement(Username username, Keyword keyword,
                                                Options options,
                                                Limit backendFetchLimit) {
        PreparedStatement statement = selectStatement(options);
        BoundStatementBuilder boundStatementBuilder = statement.boundStatementBuilder()
            .set(USERNAME, username.asString(), TypeCodecs.TEXT)
            .set(KEYWORD, keyword.flagName(), TypeCodecs.TEXT)
            .setInt(LIMIT_MARKER, backendFetchLimit.getLimit().get());

        options.after()
            .ifPresent(after -> boundStatementBuilder.setInstant(AFTER_MARKER, after));
        options.before()
            .ifPresent(before -> boundStatementBuilder.setInstant(BEFORE_MARKER, before));

        return boundStatementBuilder.build();
    }

    private PreparedStatement selectStatement(Options options) {
        boolean hasAfter = options.after().isPresent();
        boolean hasBefore = options.before().isPresent();

        if (hasAfter && hasBefore) {
            return listByKeywordRange;
        }
        if (hasAfter) {
            return listByKeywordSinceAfter;
        }
        if (hasBefore) {
            return listByKeywordBefore;
        }
        return listByKeyword;
    }
}
