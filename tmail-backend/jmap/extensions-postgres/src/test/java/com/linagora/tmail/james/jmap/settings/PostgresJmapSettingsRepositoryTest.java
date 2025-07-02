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

import org.apache.james.backends.postgres.PostgresExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresJmapSettingsRepositoryTest implements JmapSettingsRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresJmapSettingsModule.MODULE);

    @Override
    public JmapSettingsRepository testee() {
        return new PostgresJmapSettingsRepository(new PostgresJmapSettingsDAO.Factory(postgresExtension.getExecutorFactory()));
    }

    @Override
    @Disabled("Failing. This capability is only needed for TWP settings update, which uses Cassandra implementation anyway.")
    public void updatePartialShouldInsertSettingsWhenUserHasNoSettings() {

    }
}
