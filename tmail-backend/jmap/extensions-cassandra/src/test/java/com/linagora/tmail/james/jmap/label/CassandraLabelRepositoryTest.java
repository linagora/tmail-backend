package com.linagora.tmail.james.jmap.label;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraLabelRepositoryTest implements LabelRepositoryContract {
    static final CassandraModule MODULE = CassandraLabelTable.MODULE();

    private CassandraLabelRepository cassandraLabelRepository;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        cassandraLabelRepository = new CassandraLabelRepository(new CassandraLabelDAO(cassandraCluster.getConf()));
    }

    @Override
    public LabelRepository testee() {
        return cassandraLabelRepository;
    }
}
