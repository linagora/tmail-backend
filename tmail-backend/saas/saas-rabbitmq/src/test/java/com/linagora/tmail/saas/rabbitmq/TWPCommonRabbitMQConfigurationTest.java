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

package com.linagora.tmail.saas.rabbitmq;

import static com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration.TWP_QUEUES_QUORUM_BYPASS_DISABLED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Optional;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.AmqpUri;

public class TWPCommonRabbitMQConfigurationTest {
    @Test
    void shouldFallbackToDefaultConfig() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration = TWPCommonRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(twpCommonRabbitMQConfiguration).isEqualTo(new TWPCommonRabbitMQConfiguration(
            Optional.empty(),
            Optional.empty(),
            TWP_QUEUES_QUORUM_BYPASS_DISABLED));
    }

    @Test
    void parsingAmqpUrisShouldSucceedWhenValidTWPRabbitMqUri() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.rabbitmq.uri", "amqp://james:password@rabbitmqhost:5672/twp-vhost");
        rabbitConfiguration.addProperty("twp.rabbitmq.management.uri", "http://james:password@rabbitmqhost:15672/api/");

        AmqpUri amqpUri = TWPCommonRabbitMQConfiguration.from(rabbitConfiguration)
            .amqpUri().get().getFirst();
        URI managementUri = TWPCommonRabbitMQConfiguration.from(rabbitConfiguration)
            .managementUri().get();

        assertThat(amqpUri.getUri().toString()).isEqualTo("amqp://james:password@rabbitmqhost:5672/twp-vhost");
        assertThat(amqpUri.getUserInfo().username()).isEqualTo("james");
        assertThat(amqpUri.getUserInfo().password()).isEqualTo("password");
        assertThat(amqpUri.getVhost()).contains("twp-vhost");
        assertThat(amqpUri.getPort()).isEqualTo(5672);
        assertThat(managementUri.toString()).isEqualTo("http://james:password@rabbitmqhost:15672/api/");
    }

    @Test
    void amqpUrisShouldBeEmptyWhenTWPRabbitMqUriIsBlank() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.rabbitmq.uri", "  ");
        rabbitConfiguration.addProperty("twp.rabbitmq.management.uri", "  ");

        TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration = TWPCommonRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(twpCommonRabbitMQConfiguration.amqpUri()).isEmpty();
        assertThat(twpCommonRabbitMQConfiguration.managementUri()).isEmpty();
    }

    @Test
    void parsingAmqpUrisShouldThrowWhenTWPRabbitMqUriIsInvalid() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.rabbitmq.uri", "BAD_SCHEME://james:james@rabbitmqhost:5672");

        assertThatThrownBy(() -> TWPCommonRabbitMQConfiguration.from(rabbitConfiguration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid Twake RabbitMQ URI in rabbitmq.properties");
    }

    @Test
    void parsingManagementUriShouldThrowWhenTWPManagementUriIsInvalid() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.rabbitmq.management.uri", "BAD_SCHEME://james:password@rabbitmqhost:15672/api/");

        assertThatThrownBy(() -> TWPCommonRabbitMQConfiguration.from(rabbitConfiguration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("You need to specify a valid URI");
    }

    @Test
    void quorumQueuesByPassShouldBeEnabledWhenConfigured() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.queues.quorum.bypass", "true");

        TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration = TWPCommonRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(twpCommonRabbitMQConfiguration.quorumQueuesBypass()).isTrue();
    }

}
