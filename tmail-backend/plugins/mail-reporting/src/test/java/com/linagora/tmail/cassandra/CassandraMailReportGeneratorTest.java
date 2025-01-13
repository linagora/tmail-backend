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

package com.linagora.tmail.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.api.MailReportGenerator;
import com.linagora.tmail.api.MailReportGeneratorContract;

public class CassandraMailReportGeneratorTest implements MailReportGeneratorContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMailReportGenerator.MODULE);

    private CassandraMailReportGenerator cassandraMailReportGenerator;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        cassandraMailReportGenerator = new CassandraMailReportGenerator(cassandra.getConf(),
            cassandra.getTypesProvider());
        cassandraMailReportGenerator.start();
    }

    @Override
    public MailReportGenerator testee() {
        return cassandraMailReportGenerator;
    }
}
