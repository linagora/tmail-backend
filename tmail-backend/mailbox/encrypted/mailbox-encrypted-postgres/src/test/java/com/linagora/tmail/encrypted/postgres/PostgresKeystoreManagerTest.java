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

package com.linagora.tmail.encrypted.postgres;

import org.apache.james.backends.postgres.PostgresExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.KeystoreManagerContract;
import com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule;

public class PostgresKeystoreManagerTest implements KeystoreManagerContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresKeystoreModule.MODULE);

    @Override
    public KeystoreManager keyStoreManager() {
        return new PostgresKeystoreManager(new PostgresKeystoreDAO.Factory(postgresExtension.getExecutorFactory()));
    }
}
