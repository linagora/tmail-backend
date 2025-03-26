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

package com.linagora.tmail.james.jmap.label;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.cassandra.change.CassandraStateFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.jmap.model.LabelChange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraLabelChangeRepositoryTest implements LabelChangeRepositoryContract {
    static final CassandraDataDefinition MODULE = CassandraLabelChangeTable.MODULE();

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

    @Test
    void labelChangeRecordsShouldNotBeDeletedWhenTTLIsZero(CassandraCluster cassandra) throws InterruptedException {
        cassandraLabelChangeDAO = new CassandraLabelChangeDAO(cassandra.getConf(), new CassandraLabelChangesConfiguration(Duration.ofSeconds(0)));

        LabelChange labelChange = LabelChangeRepositoryContract.labelChangeFunc().apply(stateFactory());

        assertThatCode(() -> Mono.from(cassandraLabelChangeDAO.insert(labelChange)).block())
            .doesNotThrowAnyException();

        Thread.sleep(200L);

        assertThat(Flux.from(cassandraLabelChangeDAO.selectAllChanges(LabelChangeRepositoryContract.ACCOUNT_ID())).collectList().block())
            .isNotEmpty();
    }
}