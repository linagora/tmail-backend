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

package com.linagora.tmail.mailbox.quota.postgres;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition;
import org.apache.james.backends.postgres.quota.PostgresQuotaLimitDAO;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.postgres.quota.PostgresPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaChangeNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.mailbox.quota.UserQuotaReporter;
import com.linagora.tmail.mailbox.quota.UserQuotaReporterContract;

public class PostgresUserQuotaReporterTest implements UserQuotaReporterContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresQuotaDataDefinition.MODULE);

    private PostgresUserQuotaReporter postgresUserQuotaReporter;

    @BeforeEach
    void setUp() throws MailboxException {
        postgresUserQuotaReporter = new PostgresUserQuotaReporter(postgresExtension.getDefaultPostgresExecutor(), new PostgresQuotaLimitDAO(postgresExtension.getDefaultPostgresExecutor()),
            quotaRootResolver());
    }

    @Override
    public MaxQuotaManager maxQuotaManager() {
        return new PostgresPerUserMaxQuotaManager(new PostgresQuotaLimitDAO(postgresExtension.getDefaultPostgresExecutor()), QuotaChangeNotifier.NOOP);
    }

    @Override
    public UserQuotaReporter testee() {
        return postgresUserQuotaReporter;
    }
}
