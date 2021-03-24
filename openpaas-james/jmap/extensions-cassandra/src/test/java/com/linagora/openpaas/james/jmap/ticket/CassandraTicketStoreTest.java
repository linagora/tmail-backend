package com.linagora.openpaas.james.jmap.ticket;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraTicketStoreTest implements TicketStoreContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraZonedDateTimeModule.MODULE,
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
