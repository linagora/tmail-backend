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

package com.linagora.tmail.saas.rabbitmq.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.AmqpUserInfo;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;

class TWPSettingsRabbitmqModuleTest {
    private static final RabbitMQConfiguration TMAIL_RABBITMQ = RabbitMQConfiguration.builder()
        .amqpUri(URI.create("amqp://tmail:tmailpass@tmail-rabbitmq:5672"))
        .managementUri(URI.create("https://admin:admin@tmail-rabbitmq:15672/api"))
        .managementCredentials(new AmqpUserInfo("admin", "admin").asManagementCredentials())
        .build();

    @Test
    void managementUriShouldBeOnTheHostOfTWPRabbitMQUri() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("twp.rabbitmq.uri", "amqp://twp:secret@twp-rabbitmq:5672/twp");

        RabbitMQConfiguration twpRabbitMQ = twpRabbitMQConfiguration(configuration);

        assertThat(twpRabbitMQ.getManagementUri()).isEqualTo(URI.create("https://twp-rabbitmq:15672/api"));
        assertThat(twpRabbitMQ.getManagementCredentials().getUser()).isEqualTo("twp");
    }

    @Test
    void managementUriShouldBeTheConfiguredOne() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("twp.rabbitmq.uri", "amqp://twp:secret@twp-rabbitmq:5672/twp");
        configuration.addProperty("twp.rabbitmq.management.uri", "http://twp-management:8080");

        RabbitMQConfiguration twpRabbitMQ = twpRabbitMQConfiguration(configuration);

        assertThat(twpRabbitMQ.getManagementUri()).isEqualTo(URI.create("http://twp-management:8080"));
    }

    @Test
    void shouldUseTMailRabbitMQWhenNoTWPRabbitMQUri() {
        RabbitMQConfiguration twpRabbitMQ = twpRabbitMQConfiguration(new PropertiesConfiguration());

        assertThat(twpRabbitMQ).isEqualTo(TMAIL_RABBITMQ);
    }

    private RabbitMQConfiguration twpRabbitMQConfiguration(PropertiesConfiguration configuration) {
        return new TWPSettingsRabbitmqModule()
            .provideTwpSettingsRabbitMQConfiguration(TMAIL_RABBITMQ, TWPCommonRabbitMQConfiguration.from(configuration));
    }
}
