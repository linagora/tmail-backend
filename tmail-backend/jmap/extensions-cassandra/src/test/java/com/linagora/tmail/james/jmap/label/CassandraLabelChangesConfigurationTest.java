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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

class CassandraLabelChangesConfigurationTest {
    private static final Duration TOO_BIG_TTL = Duration.ofSeconds(Integer.MAX_VALUE + 1L);

    @Test
    void fromShouldReturnValuesFromSuppliedConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("label.change.ttl", "3 days");

        assertThat(CassandraLabelChangesConfiguration.from(configuration).labelChangeTtl())
            .isEqualTo(Duration.ofDays(3));
    }

    @Test
    void fromShouldFallbackToDefaultValueWhenEmptySuppliedConfiguration() {
        PropertiesConfiguration emptyConfiguration = new PropertiesConfiguration();

        assertThat(CassandraLabelChangesConfiguration.from(emptyConfiguration).labelChangeTtl())
            .isEqualTo(Duration.ofDays(60));
    }

    @Test
    void shouldThrowWhenConfiguredNegativeTTL() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("label.change.ttl", "-300");

        assertThatThrownBy(() -> CassandraLabelChangesConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptZeroTTL() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("label.change.ttl", "0");

        assertThat(CassandraLabelChangesConfiguration.from(configuration).labelChangeTtl())
            .isEqualTo(Duration.ofSeconds(0));
    }

    @Test
    void shouldThrowWhenConfiguredTooBigTTL() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("label.change.ttl", String.valueOf(TOO_BIG_TTL.getSeconds()));

        assertThatThrownBy(() -> CassandraLabelChangesConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
