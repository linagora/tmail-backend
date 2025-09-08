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
 *******************************************************************/

package com.linagora.tmail.rate.limiter.api.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.EMPTY_RATE_LIMIT;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_RECEIVED_PER_DAYS;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_RECEIVED_PER_HOURS;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_RECEIVED_PER_MINUTE;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_SENT_PER_DAYS;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_SENT_PER_HOURS;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_SENT_PER_MINUTE;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.TABLE_NAME;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.USER;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;

public class CassandraRateLimitingRepository implements RateLimitingRepository {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertRateLimitingStatement;
    private final PreparedStatement selectRateLimitingStatement;

    @Inject
    public CassandraRateLimitingRepository(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertRateLimitingStatement = session.prepare(insertInto(TABLE_NAME)
            .value(USER, bindMarker(USER))
            .value(MAILS_SENT_PER_MINUTE, bindMarker(MAILS_SENT_PER_MINUTE))
            .value(MAILS_SENT_PER_HOURS, bindMarker(MAILS_SENT_PER_HOURS))
            .value(MAILS_SENT_PER_DAYS, bindMarker(MAILS_SENT_PER_DAYS))
            .value(MAILS_RECEIVED_PER_MINUTE, bindMarker(MAILS_RECEIVED_PER_MINUTE))
            .value(MAILS_RECEIVED_PER_HOURS, bindMarker(MAILS_RECEIVED_PER_HOURS))
            .value(MAILS_RECEIVED_PER_DAYS, bindMarker(MAILS_RECEIVED_PER_DAYS))
            .build());
        this.selectRateLimitingStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(MAILS_SENT_PER_MINUTE, MAILS_SENT_PER_HOURS, MAILS_SENT_PER_DAYS,
                MAILS_RECEIVED_PER_MINUTE, MAILS_RECEIVED_PER_HOURS, MAILS_RECEIVED_PER_DAYS)
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .build());
    }

    @Override
    public Publisher<Void> setRateLimiting(Username username, RateLimitingDefinition rateLimiting) {
        return Mono.from(executor.executeVoid(insertRateLimitingStatement.bind()
            .set(USER, username.asString(), TypeCodecs.TEXT)
            .set(MAILS_SENT_PER_MINUTE, rateLimiting.mailsSentPerMinute().orElse(null), TypeCodecs.BIGINT)
            .set(MAILS_SENT_PER_HOURS, rateLimiting.mailsSentPerHours().orElse(null), TypeCodecs.BIGINT)
            .set(MAILS_SENT_PER_DAYS, rateLimiting.mailsSentPerDays().orElse(null), TypeCodecs.BIGINT)
            .set(MAILS_RECEIVED_PER_MINUTE, rateLimiting.mailsReceivedPerMinute().orElse(null), TypeCodecs.BIGINT)
            .set(MAILS_RECEIVED_PER_HOURS, rateLimiting.mailsReceivedPerHours().orElse(null), TypeCodecs.BIGINT)
            .set(MAILS_RECEIVED_PER_DAYS, rateLimiting.mailsReceivedPerDays().orElse(null), TypeCodecs.BIGINT)));
    }

    @Override
    public Publisher<RateLimitingDefinition> getRateLimiting(Username username) {
        return Mono.from(executor.executeSingleRow(selectRateLimitingStatement.bind()
                .set(USER, username.asString(), TypeCodecs.TEXT)))
            .map(this::toRateLimitingDefinition)
            .defaultIfEmpty(EMPTY_RATE_LIMIT);
    }

    private RateLimitingDefinition toRateLimitingDefinition(Row row) {
        return RateLimitingDefinition.builder()
            .mailsSentPerMinute(row.get(MAILS_SENT_PER_MINUTE, Long.class))
            .mailsSentPerHours(row.get(MAILS_SENT_PER_HOURS, Long.class))
            .mailsSentPerDays(row.get(MAILS_SENT_PER_DAYS, Long.class))
            .mailsReceivedPerMinute(row.get(MAILS_RECEIVED_PER_MINUTE, Long.class))
            .mailsReceivedPerHours(row.get(MAILS_RECEIVED_PER_HOURS, Long.class))
            .mailsReceivedPerDays(row.get(MAILS_RECEIVED_PER_DAYS, Long.class))
            .build();
    }
}
