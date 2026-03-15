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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.events.EventBus;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraLabelRepositoryTest implements LabelRepositoryContract {
    static final CassandraDataDefinition MODULE = CassandraLabelTable.MODULE();

    private final InVMEventBus inVMEventBus = new InVMEventBus(
        new InVmEventDelivery(new RecordingMetricFactory()),
        RetryBackoffConfiguration.FAST,
        new MemoryEventDeadLetters());
    private CassandraLabelRepository cassandraLabelRepository;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        cassandraLabelRepository = new CassandraLabelRepository(new CassandraLabelDAO(cassandraCluster.getConf()), inVMEventBus);
    }

    @Override
    public LabelRepository testee() {
        return cassandraLabelRepository;
    }

    @Override
    public EventBus eventBus() {
        return inVMEventBus;
    }
}
