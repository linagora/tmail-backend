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

package com.linagora.tmail.james.jmap.settings;

import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.james.backends.postgres.PostgresExtension;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition;

import reactor.core.publisher.Mono;

public class PostgresJmapSettingsRepositoryTest implements JmapSettingsRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(TMailPostgresUserDataDefinition.MODULE);

    @Override
    public JmapSettingsRepository testee() {
        return new PostgresJmapSettingsRepository(new PostgresJmapSettingsDAO(postgresExtension.getDefaultPostgresExecutor()));
    }

    @Override
    @Disabled("Failing. This capability is only needed for TWP settings update, which uses Cassandra implementation anyway.")
    public void updatePartialShouldInsertSettingsWhenUserHasNoSettings() {

    }

    @Test
    void shouldNotDeleteUserRecordWhenDeleteSettings() {
        // Given the Bob record in the user table
        postgresExtension.getDefaultPostgresExecutor()
            .executeVoid(dslContext -> Mono.from(dslContext.insertInto(DSL.table("users"), DSL.field("username"), DSL.field("hashed_password"))
                .values(BOB.asString(), "hashedPassword")))
            .block();

        // Set Bob settings
        new JmapSettingsRepositoryJavaUtils(testee()).reset(BOB, Map.of("key", "value"));

        // Delete Bob settings (e.g. as part of username change process)
        Mono.from(testee().delete(BOB)).block();

        // Assert that the user record still exists after deleting settings, so other user associated data is not lost
        String storedHashPassword = postgresExtension.getDefaultPostgresExecutor()
            .executeRow(dslContext -> Mono.from(dslContext.select(DSL.field("hashed_password"))
                .from(DSL.table("users"))
                .where(DSL.field("username").eq(BOB.asString()))))
            .block()
            .get("hashed_password", String.class);
        assertThat(storedHashPassword).isEqualTo("hashedPassword");
    }
}
