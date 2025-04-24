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

package com.linagora.tmail.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.AmqpUri;

import nl.jqno.equalsverifier.EqualsVerifier;

class OpenPaasConfigurationTest {

    @Test
    void shouldRespectBeanContract() {
        AmqpUri red = AmqpUri.from("amqp://rabbitmq.com");
        AmqpUri blue = AmqpUri.from("amqp://rabbitmq_test.com");
        EqualsVerifier.forClass(OpenPaasConfiguration.class)
            .withPrefabValues(AmqpUri.class, red, blue)
            .verify();
    }

    @Test
    void fromShouldReturnTheConfigurationWhenAllParametersAreGiven() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "123");
        configuration.addProperty("openpaas.rest.client.trust.all.ssl.certs", "true");
        configuration.addProperty("openpaas.queues.quorum.bypass", "true");
        configuration.addProperty("openpaas.rest.client.response.timeout", "500ms");

        OpenPaasConfiguration expected = new OpenPaasConfiguration(
            URI.create("http://localhost:8080"),
            "jhon_doe",
            "123",
            true,
            Duration.ofMillis(500),
            new OpenPaasConfiguration.ContactConsumerConfiguration(
                ImmutableList.of(AmqpUri.from("amqp://james:james@rabbitmqhost:5672")),
                true));

        assertThat(OpenPaasConfiguration.from(configuration))
            .isEqualTo(expected);
    }

    @Test
    void quorumQueuesBypassShouldBeDisableByDefault() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThat(OpenPaasConfiguration.from(configuration).contactConsumerConfiguration().get().quorumQueuesBypass())
            .isEqualTo(false);
    }

    @Test
    void clientTimeoutShouldBe30SecondsByDefault() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThat(OpenPaasConfiguration.from(configuration).responseTimeout())
            .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void trustAllSslCertsShouldBeDisableByDefault() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThat(OpenPaasConfiguration.from(configuration).trustAllSslCerts())
            .isEqualTo(false);
    }

    @Test
    void fromShouldThrowWhenOpenPaasApiUriNotConfigured() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThatThrownBy(() -> OpenPaasConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OpenPaas API URI not specified.");
    }

    @Test
    void fromShouldThrowWhenOpenPaasApiUriIsBlank() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.api.uri", "   ");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThatThrownBy(() -> OpenPaasConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OpenPaas API URI not specified.");
    }

    @Test
    void fromShouldThrowWhenOpenPaasApiUriIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.api.uri", "BAD_URI");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThatThrownBy(() -> OpenPaasConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Invalid OpenPaas API URI in openpaas.properties.");
    }

    @Test
    void fromShouldThrowWhenOpenPaasAdminUserNotConfigured() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThatThrownBy(() -> OpenPaasConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OpenPaas admin user not specified.");
    }

    @Test
    void fromShouldThrowWhenOpenPaasAdminUserIsBlank() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "     ");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThatThrownBy(() -> OpenPaasConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OpenPaas admin user not specified.");
    }

    @Test
    void fromShouldThrowWhenOpenPaasAdminPasswordNotConfigured() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");

        assertThatThrownBy(() -> OpenPaasConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OpenPaas admin password not specified.");
    }

    @Test
    void fromShouldThrowWhenOpenPaasAdminPasswordIsBlank() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "   ");

        assertThatThrownBy(() -> OpenPaasConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OpenPaas admin password not specified.");
    }

    @Test
    void contactConsumerConfigurationShouldReturnEmptyWhenRabbitMqURINotConfigured() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThat(OpenPaasConfiguration.from(configuration).contactConsumerConfiguration())
            .isEmpty();
    }

    @Test
    void contactConsumerConfigurationShouldEmptyWhenRabbitMqUriIsBlank() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "  ");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThat(OpenPaasConfiguration.from(configuration).contactConsumerConfiguration())
            .isEmpty();
    }

    @Test
    void fromShouldThrowWhenConfiguredRabbitMqURIisInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "BAD_SCHEME://james:james@rabbitmqhost:5672");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThatThrownBy(() -> OpenPaasConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Invalid RabbitMQ URI in openpaas.properties.");
    }
}