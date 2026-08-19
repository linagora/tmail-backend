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

import static org.mockito.Mockito.mock;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition;
import org.apache.james.backends.postgres.quota.PostgresQuotaCurrentValueDAO;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.postgres.PostgresDomainDataDefinition;
import org.apache.james.domainlist.postgres.PostgresDomainList;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.postgres.quota.PostgresCurrentQuotaManager;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.postgres.PostgresUserDataDefinition;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.james.user.postgres.PostgresUsersRepository;
import org.apache.james.user.postgres.PostgresUsersRepositoryConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.mailbox.quota.QuotaSumDao;
import com.linagora.tmail.mailbox.quota.QuotaSumDaoContract;

public class PostgresQuotaSumDaoTest implements QuotaSumDaoContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresDataDefinition.aggregateModules(
        PostgresQuotaDataDefinition.MODULE,
        PostgresDomainDataDefinition.MODULE,
        PostgresUserDataDefinition.MODULE));

    private PostgresQuotaSumDao testee;
    private CurrentQuotaManager currentQuotaManager;

    @BeforeEach
    void setUp() throws MailboxException {
        currentQuotaManager = new PostgresCurrentQuotaManager(
            new PostgresQuotaCurrentValueDAO(postgresExtension.getDefaultPostgresExecutor()));
        testee = new PostgresQuotaSumDao(postgresExtension.getDefaultPostgresExecutor(),
            usersRepository(), userQuotaRootResolver(), currentQuotaManager);
    }

    @Override
    public QuotaSumDao testee() {
        return testee;
    }

    @Override
    public CurrentQuotaManager currentQuotaManager() {
        return currentQuotaManager;
    }

    @Override
    public UsersRepository usersRepository() throws MailboxException {
        try {
            PostgresDomainList domainList = new PostgresDomainList(mock(DNSService.class), postgresExtension.getDefaultPostgresExecutor());
            domainList.configure(DomainListConfiguration.DEFAULT);
            domainList.addDomain(DOMAIN_1);
            domainList.addDomain(DOMAIN_2);
            PostgresUsersDAO usersDAO = new PostgresUsersDAO(postgresExtension.getDefaultPostgresExecutor(), PostgresUsersRepositoryConfiguration.DEFAULT);
            PostgresUsersRepository usersRepository = new PostgresUsersRepository(domainList, usersDAO);
            usersRepository.setEnableVirtualHosting(true);
            usersRepository.addUser(BOB, "pass");
            usersRepository.addUser(ALICE, "pass");
            usersRepository.addUser(ANDRE, "pass");
            return usersRepository;
        } catch (Exception e) {
            throw new MailboxException("Failed to set up the postgres users repository", e);
        }
    }
}
