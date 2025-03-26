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

package com.linagora.tmail.james.jmap.ticket;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeDataDefinition;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraTicketStoreTest implements TicketStoreContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraDataDefinition.aggregateModules(
            CassandraSchemaVersionDataDefinition.MODULE,
            CassandraZonedDateTimeDataDefinition.MODULE,
            CassandraTicketStore.module()));

    private CassandraTicketStore testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraTicketStore(cassandra.getConf(), cassandra.getTypesProvider());
    }

    @Override
    public TicketStore testee() {
        return testee;
    }
}
