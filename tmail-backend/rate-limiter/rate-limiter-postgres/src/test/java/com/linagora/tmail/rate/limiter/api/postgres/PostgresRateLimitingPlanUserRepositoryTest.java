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

package com.linagora.tmail.rate.limiter.api.postgres;

import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresExtension;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepositoryContract;
import com.linagora.tmail.rate.limiter.api.postgres.dao.PostgresRateLimitPlanUserDAO;
import com.linagora.tmail.rate.limiter.api.postgres.repository.PostgresRateLimitingPlanUserRepository;
import com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition;

import reactor.core.publisher.Mono;

class PostgresRateLimitingPlanUserRepositoryTest implements RateLimitingPlanUserRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(
        PostgresDataDefinition.aggregateModules(TMailPostgresUserDataDefinition.MODULE));

    private PostgresRateLimitingPlanUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresRateLimitingPlanUserRepository(new PostgresRateLimitPlanUserDAO(postgresExtension.getDefaultPostgresExecutor()));
    }

    @Override
    public RateLimitingPlanUserRepository testee() {
        return repository;
    }

    @Test
    void shouldNotDeleteUserRecordWhenDeleteSettings() {
        // Given the Bob record in the user table
        postgresExtension.getDefaultPostgresExecutor()
            .executeVoid(dslContext -> Mono.from(dslContext.insertInto(DSL.table("users"), DSL.field("username"), DSL.field("hashed_password"))
                .values(BOB.asString(), "hashedPassword")))
            .block();

        // Set Bob rate limiting plan
        Mono.from(repository.applyPlan(BOB, RateLimitingPlanUserRepositoryContract.PLAN_ID_1())).block();

        // Revoke Bob rate limiting plan (e.g. as part of username change process)
        Mono.from(repository.revokePlan(BOB)).block();

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
