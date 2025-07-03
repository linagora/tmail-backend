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

package com.linagora.tmail.james.jmap.settings;

import static com.linagora.tmail.james.jmap.settings.TWPCommonSettingsConfiguration.TWP_QUEUES_QUORUM_BYPASS_DISABLED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.AmqpUri;

public class TWPCommonSettingsConfigurationTest {
    @Test
    void shouldFallbackToDefaultConfig() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration).isEqualTo(new TWPCommonSettingsConfiguration(
            Optional.empty(),
            TWP_QUEUES_QUORUM_BYPASS_DISABLED,
            "settings",
            "user.settings.updated"));
    }

    @Test
    void parsingAmqpUrisShouldSucceedWhenValidTWPRabbitMqUri() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.rabbitmq.uri", "amqp://james:password@rabbitmqhost:5672/twp-vhost");

        AmqpUri amqpUri = TWPCommonSettingsConfiguration.from(rabbitConfiguration)
            .amqpUri().get().getFirst();

        assertThat(amqpUri.getUri().toString()).isEqualTo("amqp://james:password@rabbitmqhost:5672/twp-vhost");
        assertThat(amqpUri.getUserInfo().username()).isEqualTo("james");
        assertThat(amqpUri.getUserInfo().password()).isEqualTo("password");
        assertThat(amqpUri.getVhost()).contains("twp-vhost");
        assertThat(amqpUri.getPort()).isEqualTo(5672);
    }

    @Test
    void amqpUrisShouldBeEmptyWhenTWPRabbitMqUriIsBlank() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.rabbitmq.uri", "  ");

        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration.amqpUri()).isEmpty();
    }

    @Test
    void parsingAmqpUrisShouldThrowWhenTWPRabbitMqUriIsInvalid() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.rabbitmq.uri", "BAD_SCHEME://james:james@rabbitmqhost:5672");

        assertThatThrownBy(() -> TWPCommonSettingsConfiguration.from(rabbitConfiguration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid Twake RabbitMQ URI in rabbitmq.properties");
    }

    @Test
    void quorumQueuesByPassShouldBeEnabledWhenConfigured() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.queues.quorum.bypass", "true");

        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration.quorumQueuesBypass()).isTrue();
    }

    @Test
    void configuringExchangeShouldWork() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.settings.exchange", "TWPSettingsExchange");

        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration.exchange()).isEqualTo("TWPSettingsExchange");
    }

    @Test
    void configuringRoutingKeyShouldWork() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.settings.routingKey", "TWPSettingsRoutingKey");

        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration.routingKey()).isEqualTo("TWPSettingsRoutingKey");
    }

}
