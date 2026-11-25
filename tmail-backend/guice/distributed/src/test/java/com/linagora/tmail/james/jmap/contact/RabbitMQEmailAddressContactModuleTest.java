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

package com.linagora.tmail.james.jmap.contact;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.RabbitMQEmailAddressContactConfiguration;

class RabbitMQEmailAddressContactModuleTest {
    private static RabbitMQEmailAddressContactConfiguration contactConfiguration(String uri) {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("address.contact.uri", uri);
        configuration.addProperty("address.contact.user", "contact");
        configuration.addProperty("address.contact.password", "secret");
        configuration.addProperty("address.contact.queue", "AddressContactQueue");
        return RabbitMQEmailAddressContactConfiguration.from(configuration);
    }

    private static RabbitMQConfiguration commonConfiguration(boolean useSsl, String validationStrategy) {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("uri", "amqp://rabbitmq:5672");
        configuration.addProperty("management.uri", "http://rabbitmq:15672");
        configuration.addProperty("management.user", "guest");
        configuration.addProperty("management.password", "guest");
        configuration.addProperty("ssl.enabled", useSsl);
        configuration.addProperty("ssl.management.enabled", useSsl);
        configuration.addProperty("ssl.validation.strategy", validationStrategy);
        configuration.addProperty("ssl.hostname.verifier", "default");
        return RabbitMQConfiguration.from(configuration);
    }

    @Test
    void amqpsUriShouldUseSslEvenWhenTheCommonConfigurationDoesNot() {
        RabbitMQConfiguration configuration = RabbitMQEmailAddressContactModule.contactConfiguration(
            contactConfiguration("amqps://third-party-broker:5671"), commonConfiguration(false, "default"));

        assertThat(configuration.useSsl()).isTrue();
        assertThat(configuration.useSslManagement()).isTrue();
        assertThat(configuration.getSslConfiguration().getStrategy())
            .isEqualTo(RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.DEFAULT);
        assertThat(configuration.getSslConfiguration().getHostNameVerifier())
            .isEqualTo(RabbitMQConfiguration.SSLConfiguration.HostNameVerifier.DEFAULT);
    }

    @Test
    void amqpUriShouldNotUseSslWhenTheCommonConfigurationDoesNot() {
        RabbitMQConfiguration configuration = RabbitMQEmailAddressContactModule.contactConfiguration(
            contactConfiguration("amqp://rabbitmq:5672"), commonConfiguration(false, "default"));

        assertThat(configuration.useSsl()).isFalse();
    }

    @Test
    void amqpUriShouldNotUseSslEvenWhenTheMainBrokerDoes() {
        RabbitMQConfiguration configuration = RabbitMQEmailAddressContactModule.contactConfiguration(
            contactConfiguration("amqp://contacts-broker:5672"), commonConfiguration(true, "ignore"));

        assertThat(configuration.useSsl()).isFalse();
        assertThat(configuration.useSslManagement()).isFalse();
    }

    @Test
    void sslSettingsOfTheCommonConfigurationShouldApplyToAnAmqpsUri() {
        RabbitMQConfiguration configuration = RabbitMQEmailAddressContactModule.contactConfiguration(
            contactConfiguration("amqps://third-party-broker:5671"), commonConfiguration(true, "ignore"));

        assertThat(configuration.useSsl()).isTrue();
        assertThat(configuration.useSslManagement()).isTrue();
        assertThat(configuration.getSslConfiguration().getStrategy())
            .isEqualTo(RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy.IGNORE);
    }

    @Test
    void contactSettingsShouldBeKept() {
        RabbitMQConfiguration configuration = RabbitMQEmailAddressContactModule.contactConfiguration(
            contactConfiguration("amqps://third-party-broker:5671"), commonConfiguration(false, "default"));

        assertThat(configuration.getUri().toString()).isEqualTo("amqps://third-party-broker:5671");
        assertThat(configuration.getManagementCredentials().getUser()).isEqualTo("contact");
    }
}
