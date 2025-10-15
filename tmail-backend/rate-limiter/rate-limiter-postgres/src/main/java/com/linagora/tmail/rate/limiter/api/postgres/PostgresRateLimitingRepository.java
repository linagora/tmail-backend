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

package com.linagora.tmail.rate.limiter.api.postgres;

import static com.linagora.tmail.domainlist.postgres.TMailPostgresDomainDataDefinition.PostgresDomainTable.DOMAIN;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_RECEIVED_PER_DAYS;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_RECEIVED_PER_HOURS;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_RECEIVED_PER_MINUTE;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_SENT_PER_DAYS;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_SENT_PER_HOURS;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_SENT_PER_MINUTE;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.TABLE_NAME;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.USERNAME;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.jooq.Record;
import org.reactivestreams.Publisher;

import com.linagora.tmail.domainlist.postgres.TMailPostgresDomainDataDefinition;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;

public class PostgresRateLimitingRepository implements RateLimitingRepository {
    private final PostgresExecutor executor;

    @Inject
    public PostgresRateLimitingRepository(PostgresExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Publisher<Void> setRateLimiting(Username username, RateLimitingDefinition rateLimiting) {
        return executor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE_NAME)
            .set(USERNAME, username.asString())
            .set(MAILS_SENT_PER_MINUTE, rateLimiting.mailsSentPerMinute().orElse(null))
            .set(MAILS_SENT_PER_HOURS, rateLimiting.mailsSentPerHours().orElse(null))
            .set(MAILS_SENT_PER_DAYS, rateLimiting.mailsSentPerDays().orElse(null))
            .set(MAILS_RECEIVED_PER_MINUTE, rateLimiting.mailsReceivedPerMinute().orElse(null))
            .set(MAILS_RECEIVED_PER_HOURS, rateLimiting.mailsReceivedPerHours().orElse(null))
            .set(MAILS_RECEIVED_PER_DAYS, rateLimiting.mailsReceivedPerDays().orElse(null))
            .onConflict(USERNAME)
            .doUpdate()
            .set(MAILS_SENT_PER_MINUTE, rateLimiting.mailsSentPerMinute().orElse(null))
            .set(MAILS_SENT_PER_HOURS, rateLimiting.mailsSentPerHours().orElse(null))
            .set(MAILS_SENT_PER_DAYS, rateLimiting.mailsSentPerDays().orElse(null))
            .set(MAILS_RECEIVED_PER_MINUTE, rateLimiting.mailsReceivedPerMinute().orElse(null))
            .set(MAILS_RECEIVED_PER_HOURS, rateLimiting.mailsReceivedPerHours().orElse(null))
            .set(MAILS_RECEIVED_PER_DAYS, rateLimiting.mailsReceivedPerDays().orElse(null))));
    }

    @Override
    public Publisher<Void> setRateLimiting(Domain domain, RateLimitingDefinition rateLimiting) {
        return executor.executeVoid(dsl -> Mono.from(dsl.insertInto(TMailPostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
            .set(DOMAIN, domain.asString())
            .set(MAILS_SENT_PER_MINUTE, rateLimiting.mailsSentPerMinute().orElse(null))
            .set(MAILS_SENT_PER_HOURS, rateLimiting.mailsSentPerHours().orElse(null))
            .set(MAILS_SENT_PER_DAYS, rateLimiting.mailsSentPerDays().orElse(null))
            .set(MAILS_RECEIVED_PER_MINUTE, rateLimiting.mailsReceivedPerMinute().orElse(null))
            .set(MAILS_RECEIVED_PER_HOURS, rateLimiting.mailsReceivedPerHours().orElse(null))
            .set(MAILS_RECEIVED_PER_DAYS, rateLimiting.mailsReceivedPerDays().orElse(null))
            .onConflict(DOMAIN)
            .doUpdate()
            .set(MAILS_SENT_PER_MINUTE, rateLimiting.mailsSentPerMinute().orElse(null))
            .set(MAILS_SENT_PER_HOURS, rateLimiting.mailsSentPerHours().orElse(null))
            .set(MAILS_SENT_PER_DAYS, rateLimiting.mailsSentPerDays().orElse(null))
            .set(MAILS_RECEIVED_PER_MINUTE, rateLimiting.mailsReceivedPerMinute().orElse(null))
            .set(MAILS_RECEIVED_PER_HOURS, rateLimiting.mailsReceivedPerHours().orElse(null))
            .set(MAILS_RECEIVED_PER_DAYS, rateLimiting.mailsReceivedPerDays().orElse(null))));
    }

    @Override
    public Publisher<RateLimitingDefinition> getRateLimiting(Username username) {
        return Mono.from(executor.executeRow(dsl -> Mono.from(
                dsl.select(MAILS_SENT_PER_MINUTE, MAILS_SENT_PER_HOURS, MAILS_SENT_PER_DAYS,
                        MAILS_RECEIVED_PER_MINUTE, MAILS_RECEIVED_PER_HOURS, MAILS_RECEIVED_PER_DAYS)
                    .from(TABLE_NAME)
                    .where(USERNAME.eq(username.asString())))))
            .map(this::toRateLimitingDefinition)
            .defaultIfEmpty(RateLimitingDefinition.EMPTY_RATE_LIMIT);
    }

    @Override
    public Publisher<RateLimitingDefinition> getRateLimiting(Domain domain) {
        return Mono.from(executor.executeRow(dsl -> Mono.from(
                dsl.select(MAILS_SENT_PER_MINUTE, MAILS_SENT_PER_HOURS, MAILS_SENT_PER_DAYS,
                        MAILS_RECEIVED_PER_MINUTE, MAILS_RECEIVED_PER_HOURS, MAILS_RECEIVED_PER_DAYS)
                    .from(TMailPostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
                    .where(DOMAIN.eq(domain.asString())))))
            .map(this::toRateLimitingDefinition)
            .defaultIfEmpty(RateLimitingDefinition.EMPTY_RATE_LIMIT);
    }

    @Override
    public Publisher<Void> revokeRateLimiting(Username username) {
        return executor.executeVoid(dsl -> Mono.from(dsl.update(TABLE_NAME)
            .set(MAILS_SENT_PER_MINUTE, (Long) null)
            .set(MAILS_SENT_PER_HOURS, (Long) null)
            .set(MAILS_SENT_PER_DAYS, (Long) null)
            .set(MAILS_RECEIVED_PER_MINUTE, (Long) null)
            .set(MAILS_RECEIVED_PER_HOURS, (Long) null)
            .set(MAILS_RECEIVED_PER_DAYS, (Long) null)
            .where(USERNAME.eq(username.asString()))));
    }

    private RateLimitingDefinition toRateLimitingDefinition(Record row) {
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
