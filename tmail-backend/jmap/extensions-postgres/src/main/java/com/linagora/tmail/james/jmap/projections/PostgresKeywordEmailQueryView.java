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

import static com.linagora.tmail.james.jmap.projections.table.PostgresKeywordEmailQueryViewTable.KEYWORD;
import static com.linagora.tmail.james.jmap.projections.table.PostgresKeywordEmailQueryViewTable.KEYWORD_VIEW_PK_CONSTRAINT_NAME;
import static com.linagora.tmail.james.jmap.projections.table.PostgresKeywordEmailQueryViewTable.MESSAGE_ID;
import static com.linagora.tmail.james.jmap.projections.table.PostgresKeywordEmailQueryViewTable.RECEIVED_AT;
import static com.linagora.tmail.james.jmap.projections.table.PostgresKeywordEmailQueryViewTable.TABLE_NAME;
import static com.linagora.tmail.james.jmap.projections.table.PostgresKeywordEmailQueryViewTable.THREAD_ID;
import static com.linagora.tmail.james.jmap.projections.table.PostgresKeywordEmailQueryViewTable.USERNAME;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Function;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.projections.EmailQueryViewUtils;
import org.apache.james.jmap.api.projections.EmailQueryViewUtils.EmailEntry;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.util.streams.Limit;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.SelectLimitPercentStep;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresKeywordEmailQueryView implements KeywordEmailQueryView {
    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresKeywordEmailQueryView(@Named(PostgresExecutor.BY_PASS_RLS_INJECT) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    @Override
    public Mono<Void> save(Username username, Keyword keyword, Instant receivedAt, MessageId messageId, ThreadId threadId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(USERNAME, username.asString())
            .set(KEYWORD, keyword.flagName())
            .set(RECEIVED_AT, OffsetDateTime.ofInstant(receivedAt, ZoneOffset.UTC))
            .set(MESSAGE_ID, ((PostgresMessageId) messageId).asUuid())
            .set(THREAD_ID, ((PostgresMessageId) threadId.getBaseMessageId()).asUuid())
            .onConflictOnConstraint(KEYWORD_VIEW_PK_CONSTRAINT_NAME)
            .doNothing()));
    }

    @Override
    public Mono<Void> delete(Username username, Keyword keyword, Instant receivedAt, MessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(USERNAME.eq(username.asString()))
            .and(KEYWORD.eq(keyword.flagName()))
            .and(RECEIVED_AT.eq(OffsetDateTime.ofInstant(receivedAt, ZoneOffset.UTC)))
            .and(MESSAGE_ID.eq(((PostgresMessageId) messageId).asUuid()))));
    }

    @Override
    public Flux<MessageId> listMessagesByKeyword(Username username, Keyword keyword, Options options) {
        return EmailQueryViewUtils.QueryViewExtender.of(options.limit(), options.collapseThread())
            .resolve(backendFetchLimit -> postgresExecutor.executeRows(dslContext -> Flux.from(buildSelectStatement(dslContext, username, keyword, options, backendFetchLimit)))
                .map(asEmailEntry()));
    }

    @Override
    public Mono<Void> clearAll() {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.truncate(TABLE_NAME)));
    }

    private SelectLimitPercentStep buildSelectStatement(DSLContext dslContext, Username username, Keyword keyword, Options options, Limit backendFetchLimit) {
         SelectConditionStep selectStep = dslContext.select(MESSAGE_ID, RECEIVED_AT, THREAD_ID)
            .from(TABLE_NAME)
            .where(USERNAME.eq(username.asString()))
            .and(KEYWORD.eq(keyword.flagName()));

        options.after()
            .map(after -> selectStep.and(RECEIVED_AT.greaterOrEqual(OffsetDateTime.ofInstant(after, ZoneOffset.UTC))));

        options.before()
            .map(before -> selectStep.and(RECEIVED_AT.lessThan(OffsetDateTime.ofInstant(before, ZoneOffset.UTC))));

        return selectStep.orderBy(RECEIVED_AT.desc())
            .limit(backendFetchLimit.getLimit().get());
    }

    private Function<Record, EmailEntry> asEmailEntry() {
        return (Record record) -> {
            PostgresMessageId messageId = PostgresMessageId.Factory.of(record.get(MESSAGE_ID));
            ThreadId threadId = Optional.ofNullable(record.get(THREAD_ID))
                .map(uuid -> ThreadId.fromBaseMessageId(PostgresMessageId.Factory.of(uuid)))
                .orElseGet(() -> ThreadId.fromBaseMessageId(messageId));
            Instant messageDate = record.get(RECEIVED_AT).toInstant();
            return new EmailEntry(messageId, threadId, messageDate);
        };
    }
}
