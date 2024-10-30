package com.linagora.tmail;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.rabbitmq.client.ConnectionFactory;

class AmqpUriTest {
    private static Stream<Arguments> goodAmqpURIs() {
        return Stream.of(
            Arguments.of("amqp://guest:guest@localhost:5672/"),
            Arguments.of("amqp://user:password@host:port/vhost"),
            Arguments.of("amqps://user:password@securehost:5671/securevhost"), // Using AMQPS for secure connection
            Arguments.of("amqp://@localhost:5672/"),
            Arguments.of("amqp://:password@host"),
            Arguments.of("amqp://user@host")
            );
    }

    private static Stream<Arguments> badAmqpURIs() {
        return Stream.of(
            Arguments.of("http://guest:guest@localhost:5672/"), // Wrong protocol
            Arguments.of("amqp://user:pass@host:5672/extra/path"),      // Extra path element
            Arguments.of("BAD_URI")                                     // Just bad
        );
    }

    @ParameterizedTest
    @MethodSource("goodAmqpURIs")
    void testGoodAmpqUriString(String amqpUri) {
        assertThatCode(() -> AmqpUri.from(amqpUri))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("badAmqpURIs")
    void testBadAmpqUriString(String amqpUri) {
        Assertions.assertThatThrownBy(() -> AmqpUri.from(amqpUri));
    }

    @Test
    void shouldRespectBeanContract() throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
        ConnectionFactory red = new ConnectionFactory();
        ConnectionFactory blue = new ConnectionFactory();

        red.setUri("amqp://username@rabbitmq.com");
        blue.setUri("amqp://rabbitmq_test.com");

        EqualsVerifier
            .forClass(AmqpUri.class)
            .withIgnoredFields("connectionFactory")
            .withPrefabValues(ConnectionFactory.class, red, blue)
            .verify();
    }

    @Test
    void shouldUseEmptyUsernameWhenUsernameIsMissing() {
        AmqpUri uri = AmqpUri.from("amqp://:password@rabbitmq.com/vhost");

        assertThat(uri.getUserInfo().username()).isEqualTo("");
        assertThat(uri.getUserInfo().password()).isEqualTo("password");
    }

    @Test
    void shouldUseTheDefaultPasswordWhenPasswordIsMissing() {
        AmqpUri uri = AmqpUri.from("amqp://user@rabbitmq.com/vhost");

        assertThat(uri.getUserInfo().username()).isEqualTo("user");
        assertThat(uri.getUserInfo().password()).isEqualTo("guest");
    }

    @Test
    void shouldUseTheDefaultCredentialsWhenCredentialsIsMissing() {
        AmqpUri uri = AmqpUri.from("amqp://rabbitmq.com/vhost");

        assertThat(uri.getUserInfo().username()).isEqualTo("guest");
        assertThat(uri.getUserInfo().password()).isEqualTo("guest");
    }

    @Test
    void shouldUsePort5672WhenAmqpIsUsedAndPortIsMissing() {
        AmqpUri uri = AmqpUri.from("amqp://user:pass@rabbitmq.com/vhost");

        assertThat(uri.getPort()).isEqualTo(5672);
    }

    @Test
    void shouldUseSpecificPortWhenSpecified() {
        AmqpUri uri = AmqpUri.from("amqp://user:password@rabbitmq.com:1000/vhost");

        assertThat(uri.getPort()).isEqualTo(1000);
    }

    @Test
    void shouldUsePort5671WhenAmqpOverSslIsUsedAndPortIsMissing() {
        AmqpUri uri = AmqpUri.from("amqps://user:pass@rabbitmq.com/vhost");

        assertThat(uri.getPort()).isEqualTo(5671);
    }
}