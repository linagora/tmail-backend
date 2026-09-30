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

package com.linagora.tmail.saas.rabbitmq.deletion;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

public class TWPUserDeletionRabbitMQConfigurationTest {
    @Test
    void shouldFallbackToDefaultConfig() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        TWPUserDeletionRabbitMQConfiguration configuration = TWPUserDeletionRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(configuration).isEqualTo(new TWPUserDeletionRabbitMQConfiguration(
            "auth",
            "user.deleted",
            "b2b",
            "domain.user.deleted"));
    }

    @Test
    void configuredValuesShouldBeUsed() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.user.deletion.b2c.exchange", "b2cExchange");
        rabbitConfiguration.addProperty("twp.user.deletion.b2c.routingKey", "b2cRoutingKey");
        rabbitConfiguration.addProperty("twp.user.deletion.b2b.exchange", "b2bExchange");
        rabbitConfiguration.addProperty("twp.user.deletion.b2b.routingKey", "b2bRoutingKey");

        TWPUserDeletionRabbitMQConfiguration configuration = TWPUserDeletionRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(configuration).isEqualTo(new TWPUserDeletionRabbitMQConfiguration(
            "b2cExchange",
            "b2cRoutingKey",
            "b2bExchange",
            "b2bRoutingKey"));
    }
}
