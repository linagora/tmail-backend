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

package com.linagora.tmail.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class RabbitMQEmailAddressContactConfigurationTest {

    @Test
    void amqpURIShouldSupportVhostInURI() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("address.contact.uri", "amqp://james:james@rabbitmqhost:5672/vhost1");
        configuration.addProperty("address.contact.user", "DEFAULT_USER");
        configuration.addProperty("address.contact.password", "DEFAULT_PASSWORD");
        configuration.addProperty("address.contact.queue", "DEFAULT_QUEUE");

        assertThat(RabbitMQEmailAddressContactConfiguration.from(configuration).vhost())
            .isEqualTo(Optional.of("vhost1"));
    }

    @Test
    void amqpURIShouldSupportDefaultVhost() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("address.contact.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("address.contact.user", "DEFAULT_USER");
        configuration.addProperty("address.contact.password", "DEFAULT_PASSWORD");
        configuration.addProperty("address.contact.queue", "DEFAULT_QUEUE");
        configuration.addProperty("vhost", "vhost1");

        assertThat(RabbitMQEmailAddressContactConfiguration.from(configuration).vhost())
            .isEqualTo(Optional.of("vhost1"));
    }

    @Test
    void fromConfigurationShouldFailWhenInvalidURI() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("address.contact.uri", "amqp://james:james@rabbitmqhost:5672/vhost1/invalidPath");
        configuration.addProperty("address.contact.user", "DEFAULT_USER");
        configuration.addProperty("address.contact.password", "DEFAULT_PASSWORD");
        configuration.addProperty("address.contact.queue", "DEFAULT_QUEUE");

        assertThatThrownBy(() -> RabbitMQEmailAddressContactConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromShouldReturnEmptyVhostValueWhenAQMPUriIsNotProvideVhostPath() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("address.contact.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("address.contact.user", "DEFAULT_USER");
        configuration.addProperty("address.contact.password", "DEFAULT_PASSWORD");
        configuration.addProperty("address.contact.queue", "DEFAULT_QUEUE");

        assertThat(RabbitMQEmailAddressContactConfiguration.from(configuration).vhost())
            .isEmpty();
    }

    @Test
    void fromShouldSuccessWhenProvideValidConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("address.contact.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("address.contact.user", "DEFAULT_USER");
        configuration.addProperty("address.contact.password", "DEFAULT_PASSWORD");
        configuration.addProperty("address.contact.queue", "DEFAULT_QUEUE");

        RabbitMQEmailAddressContactConfiguration contactConfiguration = RabbitMQEmailAddressContactConfiguration.from(configuration);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(contactConfiguration.queueName()).isEqualTo("DEFAULT_QUEUE");
            softly.assertThat(contactConfiguration.amqpUri().toASCIIString()).isEqualTo("amqp://james:james@rabbitmqhost:5672");
            softly.assertThat(contactConfiguration.managementCredentials()).isEqualTo(new RabbitMQConfiguration.ManagementCredentials("DEFAULT_USER", "DEFAULT_PASSWORD".toCharArray()));
            softly.assertThat(contactConfiguration.getExchangeName()).isEqualTo("TmailExchange-DEFAULT_QUEUE");
            softly.assertThat(contactConfiguration.getDeadLetterExchange()).isEqualTo("TmailQueue-dead-letter-exchange-DEFAULT_QUEUE");
            softly.assertThat(contactConfiguration.getDeadLetterQueue()).isEqualTo("TmailQueue-dead-letter-queue-DEFAULT_QUEUE");
            softly.assertThat(contactConfiguration.vhost()).isEmpty();
        });
    }

}
