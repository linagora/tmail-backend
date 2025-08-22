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

package com.linagora.tmail.saas.rabbitmq.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

public class TWPSettingsRabbitMQConfigurationTest {
    @Test
    void shouldFallbackToDefaultConfig() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        TWPSettingsRabbitMQConfiguration configuration = TWPSettingsRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(configuration).isEqualTo(new TWPSettingsRabbitMQConfiguration(
            "settings",
            "user.settings.updated"));
    }

    @Test
    void configureExchangeShouldWork() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.settings.exchange", "TWPSettingsExchange");

        TWPSettingsRabbitMQConfiguration configuration = TWPSettingsRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(configuration.exchange()).isEqualTo("TWPSettingsExchange");
    }

    @Test
    void configureRoutingKeyShouldWork() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.settings.routingKey", "TWPSettingsRoutingKey");

        TWPSettingsRabbitMQConfiguration configuration = TWPSettingsRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(configuration.routingKey()).isEqualTo("TWPSettingsRoutingKey");
    }
}
