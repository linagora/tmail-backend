package com.linagora.tmail.james.jmap.ticket.longlivedtoken;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.jmap.longlivedtoken.CassandraLongLivedTokenDAO;
import com.linagora.tmail.james.jmap.longlivedtoken.CassandraLongLivedTokenStore;
import com.linagora.tmail.james.jmap.longlivedtoken.LongLivedTokenStore;
import com.linagora.tmail.james.jmap.longlivedtoken.LongLivedTokenStoreContract;
import com.linagora.tmail.james.jmap.longlivedtoken.LongLivedTokenStoreTable;

public class CassandraLongLivedTokenStoreTest implements LongLivedTokenStoreContract {

    private CassandraLongLivedTokenStore cassandraLongLivedTokenStore;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(LongLivedTokenStoreTable.module()));

    @BeforeEach
    void beforeEach(CassandraCluster cassandra) {
        CassandraLongLivedTokenDAO cassandraLongLivedTokenDAO = new CassandraLongLivedTokenDAO(cassandra.getConf());
        cassandraLongLivedTokenStore = new CassandraLongLivedTokenStore(cassandraLongLivedTokenDAO);
    }

    @Override
    public LongLivedTokenStore testee() {
        return cassandraLongLivedTokenStore;
    }
}
