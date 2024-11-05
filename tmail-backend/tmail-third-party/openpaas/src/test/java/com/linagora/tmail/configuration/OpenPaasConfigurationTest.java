package com.linagora.tmail.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

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

        OpenPaasConfiguration expected = new OpenPaasConfiguration(
            AmqpUri.from("amqp://james:james@rabbitmqhost:5672"),
            URI.create("http://localhost:8080"),
            "jhon_doe",
            "123"
        );

        assertThat(OpenPaasConfiguration.from(configuration))
            .isEqualTo(expected);
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
    void fromShouldCrashWhenRabbitMqURINotConfigured() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThatThrownBy(() -> OpenPaasConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("RabbitMQ URI not defined in openpaas.properties.");
    }

    @Test
    void fromShouldCrashWhenRabbitMqUriIsBlank() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("rabbitmq.uri", "  ");
        configuration.addProperty("openpaas.api.uri", "http://localhost:8080");
        configuration.addProperty("openpaas.admin.user", "jhon_doe");
        configuration.addProperty("openpaas.admin.password", "123");

        assertThatThrownBy(() -> OpenPaasConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("RabbitMQ URI not defined in openpaas.properties.");
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