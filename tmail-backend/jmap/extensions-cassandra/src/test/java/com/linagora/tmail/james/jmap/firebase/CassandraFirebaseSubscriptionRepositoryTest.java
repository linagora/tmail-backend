package com.linagora.tmail.james.jmap.firebase;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.jmap.api.change.TypeStateFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import scala.jdk.javaapi.CollectionConverters;

public class CassandraFirebaseSubscriptionRepositoryTest implements FirebaseSubscriptionRepositoryContract {
    static final CassandraModule MODULE = CassandraFirebaseSubscriptionTable.MODULE();

    private UpdatableTickingClock clock;
    private FirebaseSubscriptionRepository firebaseSubscriptionRepository;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    @BeforeEach
    void setup(CassandraCluster cassandra) {
        clock = new UpdatableTickingClock(FirebaseSubscriptionRepositoryContract.NOW());
        firebaseSubscriptionRepository = new CassandraFirebaseSubscriptionRepository(
            new CassandraFirebaseSubscriptionDAO(cassandra.getConf(), new TypeStateFactory(CollectionConverters.asJava(FirebaseSubscriptionRepositoryContract.TYPE_NAME_SET()))),
            clock);
    }

    @Override
    public UpdatableTickingClock clock() {
        return clock;
    }

    @Override
    public FirebaseSubscriptionRepository testee() {
        return firebaseSubscriptionRepository;
    }
}
