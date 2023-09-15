package com.linagora.tmail.james.jmap.label;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.cassandra.change.CassandraStateFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraLabelChangeRepositoryTest implements LabelChangeRepositoryContract {
    static final CassandraModule MODULE = CassandraLabelChangeTable.MODULE();

    private CassandraLabelChangeDAO cassandraLabelChangeDAO;
    private CassandraLabelChangeRepository cassandraLabelChangeRepository;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        cassandraLabelChangeDAO = new CassandraLabelChangeDAO(cassandraCluster.getConf(), CassandraLabelChangesConfiguration.DEFAULT());
        cassandraLabelChangeRepository = new CassandraLabelChangeRepository(cassandraLabelChangeDAO);
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

    @Test
    void labelChangeRecordsShouldBeDeletedAfterTTL(CassandraCluster cassandra) {
        cassandraLabelChangeDAO = new CassandraLabelChangeDAO(cassandra.getConf(), new CassandraLabelChangesConfiguration(Duration.ofSeconds(1)));

        LabelChange labelChange = LabelChangeRepositoryContract.labelChangeFunc().apply(stateFactory());

        assertThatCode(() -> Mono.from(cassandraLabelChangeDAO.insert(labelChange)).block())
            .doesNotThrowAnyException();

        await().atMost(Duration.ofSeconds(3))
            .await()
            .untilAsserted(() -> assertThat(Flux.from(cassandraLabelChangeDAO.selectAllChanges(LabelChangeRepositoryContract.ACCOUNT_ID())).collectList().block())
                .isEmpty());
    }
}