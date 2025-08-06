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

package com.linagora.tmail.saas.api.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresExtension;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.SaaSAccountRepositoryContract;
import com.linagora.tmail.saas.model.SaaSAccount;
import com.linagora.tmail.saas.model.SaaSPlan;

import reactor.core.publisher.Mono;

public class PostgresSaaSAccountRepositoryTest implements SaaSAccountRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(
        PostgresDataDefinition.aggregateModules(PostgresSaaSDataDefinition.MODULE));

    private PostgresSaaSAccountRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresSaaSAccountRepository(postgresExtension.getDefaultPostgresExecutor());
    }

    @Override
    public SaaSAccountRepository testee() {
        return repository;
    }

    @Test
    void insertPlanShouldSucceedWhenExistingUserRecordWithoutSaaSPlan() {
        // Given the Bob record in the users table
        postgresExtension.getDefaultPostgresExecutor()
            .executeVoid(dslContext -> Mono.from(dslContext.insertInto(DSL.table("users"), DSL.field("username"), DSL.field("hashed_password"))
                .values(BOB.asString(), "hashedPassword")))
            .block();

        // Set Bob SaaS plan
        Mono.from(repository.upsertSaasAccount(BOB, new SaaSAccount(SaaSPlan.PREMIUM))).block();

        // Assert that the user record still exists and other associated data is not lost
        org.jooq.Record record = postgresExtension.getDefaultPostgresExecutor()
            .executeRow(dslContext -> Mono.from(dslContext.select(DSL.field("hashed_password"), DSL.field("saas_plan"))
                .from(DSL.table("users"))
                .where(DSL.field("username").eq(BOB.asString()))))
            .block();

        assertThat(record.get("saas_plan", String.class)).isEqualTo("premium");
        assertThat(record.get("hashed_password", String.class)).isEqualTo("hashedPassword");
    }

    @Test
    void updatePlanShouldSucceedWhenExistingUserRecordWithSaaSPlan() {
        // Given the Bob record in the user table
        postgresExtension.getDefaultPostgresExecutor()
            .executeVoid(dslContext -> Mono.from(dslContext.insertInto(DSL.table("users"), DSL.field("username"), DSL.field("hashed_password"))
                .values(BOB.asString(), "hashedPassword")))
            .block();
        Mono.from(repository.upsertSaasAccount(BOB, new SaaSAccount(SaaSPlan.STANDARD))).block();

        // Update Bob' SaaS plan from STANDARD to PREMIUM
        Mono.from(repository.upsertSaasAccount(BOB, new SaaSAccount(SaaSPlan.PREMIUM))).block();

        // Assert that the user record still exists and other associated data is not lost
        org.jooq.Record record = postgresExtension.getDefaultPostgresExecutor()
            .executeRow(dslContext -> Mono.from(dslContext.select(DSL.field("hashed_password"), DSL.field("saas_plan"))
                .from(DSL.table("users"))
                .where(DSL.field("username").eq(BOB.asString()))))
            .block();

        assertThat(record.get("saas_plan", String.class)).isEqualTo("premium");
        assertThat(record.get("hashed_password", String.class)).isEqualTo("hashedPassword");
    }
}
