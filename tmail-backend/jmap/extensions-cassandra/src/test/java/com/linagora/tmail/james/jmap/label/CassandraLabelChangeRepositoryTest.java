package com.linagora.tmail.james.jmap.label;

import java.time.ZonedDateTime;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.cassandra.change.CassandraStateFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraLabelChangeRepositoryTest implements LabelChangeRepositoryContract {
    static final CassandraModule MODULE = CassandraLabelChangeTable.MODULE();

    private CassandraLabelChangeRepository cassandraLabelChangeRepository;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        cassandraLabelChangeRepository = new CassandraLabelChangeRepository(LabelChangeRepositoryContract.defaultLimit(),
            new CassandraLabelChangeDAO(cassandraCluster.getConf()));
    }

    @Override
    public LabelChangeRepository testee() {
        return cassandraLabelChangeRepository;
    }

    @Override
    public State.Factory stateFactory() {
        return new CassandraStateFactory();
    }

    @Override
    public void setClock(ZonedDateTime newTime) {

    }
}
