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

package com.linagora.tmail.encrypted.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.KeystoreManagerContract;
import com.linagora.tmail.encrypted.cassandra.table.CassandraKeystoreModule;

public class CassandraKeystoreManagerTest implements KeystoreManagerContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraDataDefinition.aggregateModules(CassandraKeystoreModule.MODULE(),
            CassandraSchemaVersionDataDefinition.MODULE));

    private KeystoreManager keystore;
    private CassandraKeystoreDAO cassandraKeystoreDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        cassandraKeystoreDAO = new CassandraKeystoreDAO(cassandra.getConf());
        keystore = new CassandraKeystoreManager(cassandraKeystoreDAO);
    }

    @Override
    public KeystoreManager keyStoreManager() {
        return keystore;
    }
}
