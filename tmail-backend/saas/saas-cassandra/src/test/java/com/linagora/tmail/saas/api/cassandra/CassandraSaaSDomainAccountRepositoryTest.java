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

package com.linagora.tmail.saas.api.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.domainlist.cassandra.TMailCassandraDomainListDataDefinition;
import com.linagora.tmail.saas.api.SaaSDomainAccountRepository;
import com.linagora.tmail.saas.api.SaaSDomainAccountRepositoryContract;

public class CassandraSaaSDomainAccountRepositoryTest implements SaaSDomainAccountRepositoryContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(TMailCassandraDomainListDataDefinition.MODULE);

    private CassandraSaaSDomainAccountRepository repository;

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        repository = new CassandraSaaSDomainAccountRepository(cassandraCluster.getConf());
    }

    @Override
    public SaaSDomainAccountRepository testee() {
        return repository;
    }
}
