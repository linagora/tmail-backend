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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.james.jmap.settings;

import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition;

import reactor.core.publisher.Mono;

public class CassandraJmapSettingsRepositoryTest implements JmapSettingsRepositoryContract {
    private static final CassandraDataDefinition MODULE = TMailCassandraUsersRepositoryDataDefinition.MODULE;
    private CassandraJmapSettingsRepository cassandraJmapSettingsRepository;
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        cassandraJmapSettingsRepository = new CassandraJmapSettingsRepository(new CassandraJmapSettingsDAO(cassandraCluster.getConf()));
    }

    @Override
    public JmapSettingsRepository testee() {
        return cassandraJmapSettingsRepository;
    }

    @Test
    void shouldNotDeleteUserRecordWhenDeleteSettings(CassandraCluster cassandraCluster) {
        CqlSession testingSession = cassandraCluster.getConf();

        // Given the Bob record in the user table
        testingSession.execute(String.format("INSERT INTO user (name, realname) VALUES ('%s', '%s')",  BOB.asString(), BOB.getLocalPart()));

        // Set Bob settings
        Mono.from(cassandraJmapSettingsRepository.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(
            JmapSettingsKey.liftOrThrow("key"), "value"))).block();

        // Delete Bob settings (e.g. as part of username change process)
        Mono.from(cassandraJmapSettingsRepository.delete(BOB)).block();

        // Assert that the user record still exists after deleting settings, so other user associated data is not lost
        assertThat(testingSession.execute(String.format("SELECT * FROM user WHERE name = '%s'",  BOB.asString()))
            .iterator()
            .next()
            .get("realname", String.class))
            .isEqualTo(BOB.getLocalPart());
    }
}
