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
import org.apache.james.backends.cassandra.components.CassandraQuotaLimitDao;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaManagerV2;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaChangeNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.mailbox.quota.UserQuotaReporter;
import com.linagora.tmail.mailbox.quota.UserQuotaReporterContract;

public class CassandraUserQuotaReporterTest implements UserQuotaReporterContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMutualizedQuotaDataDefinition.MODULE);

    private CassandraUserQuotaReporter cassandraUserQuotaReporter;

    @BeforeEach
    void setUp() {
        cassandraUserQuotaReporter = new CassandraUserQuotaReporter(cassandraCluster.getCassandraCluster().getConf(), new CassandraQuotaLimitDao(cassandraCluster.getCassandraCluster().getConf()));
    }

    @Override
    public MaxQuotaManager maxQuotaManager() {
        return new CassandraPerUserMaxQuotaManagerV2(new CassandraQuotaLimitDao(cassandraCluster.getCassandraCluster().getConf()),
            QuotaChangeNotifier.NOOP);
    }

    @Override
    public UserQuotaReporter testee() {
        return cassandraUserQuotaReporter;
    }

}
