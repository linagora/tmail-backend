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

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaDataDefinition;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManagerV2;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.mailbox.quota.QuotaSumDao;
import com.linagora.tmail.mailbox.quota.QuotaSumDaoContract;

public class CassandraQuotaSumDaoTest implements QuotaSumDaoContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMutualizedQuotaDataDefinition.MODULE);

    private CassandraQuotaSumDao testee;
    private CurrentQuotaManager currentQuotaManager;

    @BeforeEach
    void setUp() throws MailboxException {
        testee = new CassandraQuotaSumDao(cassandraCluster.getCassandraCluster().getConf(), quotaRootResolver());
        currentQuotaManager = new CassandraCurrentQuotaManagerV2(
            new CassandraQuotaCurrentValueDao(cassandraCluster.getCassandraCluster().getConf()));
    }

    @Override
    public QuotaSumDao testee() {
        return testee;
    }

    @Override
    public CurrentQuotaManager currentQuotaManager() {
        return currentQuotaManager;
    }
}
