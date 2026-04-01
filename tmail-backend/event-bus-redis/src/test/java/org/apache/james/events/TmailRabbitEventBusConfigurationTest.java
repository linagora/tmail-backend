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
 *******************************************************************/

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

public class TmailRabbitEventBusConfigurationTest {
    @Test
    void shouldDefaultToOnePartitionWhenPartitionCountIsMissing() {
        PropertiesConfiguration rabbitMqConfiguration = new PropertiesConfiguration();

        TmailRabbitEventBusConfiguration configuration = TmailRabbitEventBusConfiguration.from(rabbitMqConfiguration);

        assertThat(configuration.partitionCount()).isEqualTo(1);
    }

    @Test
    void shouldParsePartitionCount() {
        PropertiesConfiguration rabbitMqConfiguration = new PropertiesConfiguration();
        rabbitMqConfiguration.addProperty("event.bus.partition.count", 3);

        TmailRabbitEventBusConfiguration configuration = TmailRabbitEventBusConfiguration.from(rabbitMqConfiguration);

        assertThat(configuration.partitionCount()).isEqualTo(3);
    }

    @Test
    void shouldRejectNonPositivePartitionCount() {
        PropertiesConfiguration rabbitMqConfiguration = new PropertiesConfiguration();
        rabbitMqConfiguration.addProperty("event.bus.partition.count", 0);

        assertThatThrownBy(() -> TmailRabbitEventBusConfiguration.from(rabbitMqConfiguration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("event.bus.partition.count must be strictly positive");
    }

    @Test
    void shouldRejectNegativePartitionCount() {
        PropertiesConfiguration rabbitMqConfiguration = new PropertiesConfiguration();
        rabbitMqConfiguration.addProperty("event.bus.partition.count", -1);

        assertThatThrownBy(() -> TmailRabbitEventBusConfiguration.from(rabbitMqConfiguration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("event.bus.partition.count must be strictly positive");
    }
}
