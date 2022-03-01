package com.linagora.tmail.encrypted.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.KeystoreManagerContract;
import com.linagora.tmail.encrypted.cassandra.table.CassandraKeystoreModule;

public class CassandraKeystoreManagerTest implements KeystoreManagerContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraKeystoreModule.MODULE(),
            CassandraSchemaVersionModule.MODULE));

    private KeystoreManager keystore;
    private CassandraKeystoreDAO cassandraKeystoreDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        cassandraKeystoreDAO = new CassandraKeystoreDAO(cassandra.getConf(), cassandra.getTypesProvider());
        keystore = new CassandraKeystoreManager(cassandraKeystoreDAO);
    }

    @Override
    public KeystoreManager keyStoreManager() {
        return keystore;
    }
}
