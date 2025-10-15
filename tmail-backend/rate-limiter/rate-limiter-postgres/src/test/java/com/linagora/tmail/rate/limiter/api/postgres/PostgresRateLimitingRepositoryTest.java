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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresExtension;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.domainlist.postgres.TMailPostgresDomainDataDefinition;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepositoryContract;
import com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition;

import reactor.core.publisher.Mono;

public class PostgresRateLimitingRepositoryTest implements RateLimitingRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(
        PostgresDataDefinition.aggregateModules(TMailPostgresUserDataDefinition.MODULE, TMailPostgresDomainDataDefinition.MODULE));

    private PostgresRateLimitingRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresRateLimitingRepository(postgresExtension.getDefaultPostgresExecutor());
    }

    @Override
    public RateLimitingRepository testee() {
        return repository;
    }

    @Test
    void insertPlanShouldSucceedWhenExistingUserRecord() {
        // Given the Bob record in the users table
        postgresExtension.getDefaultPostgresExecutor()
            .executeVoid(dslContext -> Mono.from(dslContext.insertInto(DSL.table("users"), DSL.field("username"), DSL.field("hashed_password"))
                .values(BOB.asString(), "hashedPassword")))
            .block();

        // Set Bob rate limits
        Mono.from(repository.setRateLimiting(BOB, RATE_LIMITING_1)).block();

        // Assert that the user record still exists and other associated data is not lost
        org.jooq.Record record = postgresExtension.getDefaultPostgresExecutor()
            .executeRow(dslContext -> Mono.from(dslContext.select(DSL.field("hashed_password"), DSL.field("mails_sent_per_minute"))
                .from(DSL.table("users"))
                .where(DSL.field("username").eq(BOB.asString()))))
            .block();

        assertThat(record.get("hashed_password", String.class))
            .isEqualTo("hashedPassword");
        assertThat(record.get("mails_sent_per_minute", Long.class))
            .isEqualTo(RATE_LIMITING_1.mailsSentPerMinute().orElse(null));
    }

    @Test
    void shouldNotDeleteUserRecordWhenRevokeRateLimiting() {
        // Given the Bob record in the user table
        postgresExtension.getDefaultPostgresExecutor()
            .executeVoid(dslContext -> Mono.from(dslContext.insertInto(DSL.table("users"), DSL.field("username"), DSL.field("hashed_password"))
                .values(BOB.asString(), "hashedPassword")))
            .block();

        // Set Bob rate limits
        Mono.from(repository.setRateLimiting(BOB, RATE_LIMITING_1)).block();

        // Revoke Bob rate limits
        Mono.from(repository.revokeRateLimiting(BOB)).block();

        // Assert that the user record still exists after revoke associated plan, so other user associated data is not lost
        String storedHashPassword = postgresExtension.getDefaultPostgresExecutor()
            .executeRow(dslContext -> Mono.from(dslContext.select(DSL.field("hashed_password"))
                .from(DSL.table("users"))
                .where(DSL.field("username").eq(BOB.asString()))))
            .block()
            .get("hashed_password", String.class);
        assertThat(storedHashPassword).isEqualTo("hashedPassword");
    }
}
