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

package com.linagora.tmail.mailbox.quota.cassandra;

import static org.mockito.Mockito.mock;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaDataDefinition;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.cassandra.CassandraDomainList;
import org.apache.james.domainlist.cassandra.CassandraDomainListDataDefinition;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManagerV2;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.cassandra.CassandraUsersRepositoryDataDefinition;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.linagora.tmail.mailbox.quota.QuotaSumDao;
import com.linagora.tmail.mailbox.quota.QuotaSumDaoContract;

public class CassandraQuotaSumDaoTest implements QuotaSumDaoContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraDataDefinition.aggregateModules(
        CassandraMutualizedQuotaDataDefinition.MODULE,
        CassandraDomainListDataDefinition.MODULE,
        CassandraUsersRepositoryDataDefinition.MODULE));

    private CassandraQuotaSumDao testee;
    private CurrentQuotaManager currentQuotaManager;

    @BeforeEach
    void setUp() throws MailboxException {
        currentQuotaManager = new CassandraCurrentQuotaManagerV2(
            new CassandraQuotaCurrentValueDao(cassandraCluster.getCassandraCluster().getConf()));
        testee = new CassandraQuotaSumDao(cassandraCluster.getCassandraCluster().getConf(),
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
            CqlSession session = cassandraCluster.getCassandraCluster().getConf();
            CassandraDomainList domainList = new CassandraDomainList(mock(DNSService.class), session);
            domainList.configure(DomainListConfiguration.DEFAULT);
            domainList.addDomain(DOMAIN_1);
            domainList.addDomain(DOMAIN_2);
            UsersRepositoryImpl<CassandraUsersDAO> usersRepository = new UsersRepositoryImpl<>(domainList, new CassandraUsersDAO(session));
            usersRepository.setEnableVirtualHosting(true);
            usersRepository.addUser(BOB, "pass");
            usersRepository.addUser(ALICE, "pass");
            usersRepository.addUser(ANDRE, "pass");
            return usersRepository;
        } catch (Exception e) {
            throw new MailboxException("Failed to set up the cassandra users repository", e);
        }
    }
}
