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

package com.linagora.tmail.james.app;

import static com.linagora.tmail.modules.data.TMailPostgresUsersRepositoryModule.TMAIL_POSTGRES_USER;

import jakarta.inject.Named;

import org.apache.james.backends.postgres.PostgresTable;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.james.jmap.saas.SaaSCapabilitiesModule;
import com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition;

public class PostgresSaaSModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new SaaSCapabilitiesModule());
    }

    @Provides
    @Singleton
    @Named(TMAIL_POSTGRES_USER)
    public PostgresTable.CreateTableFunction overrideCreateUserTableFunction() {
        return PostgresSaaSDataDefinition.userTableWithSaaSSupport();
    }
}
