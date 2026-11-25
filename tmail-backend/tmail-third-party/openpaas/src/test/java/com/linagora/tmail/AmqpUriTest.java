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

package com.linagora.tmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import nl.jqno.equalsverifier.EqualsVerifier;

class AmqpUriTest {
    private static final RabbitMQConfiguration COMMON_RABBITMQ = commonRabbitMQ("https://admin:admin@common-rabbitmq:15672/api");

    private static RabbitMQConfiguration commonRabbitMQ(String managementUri) {
        return RabbitMQConfiguration.builder()
            .amqpUri(URI.create("amqp://common:commonpass@common-rabbitmq:5672"))
            .managementUri(URI.create(managementUri))
            .managementCredentials(new AmqpUserInfo("admin", "admin").asManagementCredentials())
            .build();
    }

    private static Stream<Arguments> goodAmqpURIs() {
        return Stream.of(
            Arguments.of("amqp://guest:guest@localhost:5672/"),
            Arguments.of("amqp://user:password@host:port/vhost"),
            Arguments.of("amqps://user:password@securehost:5671/securevhost"), // Using AMQPS for secure connection
            Arguments.of("amqp://@localhost:5672/"),
            Arguments.of("amqp://:password@host"),
            Arguments.of("amqp://user@host"));
    }

    private static Stream<Arguments> badAmqpURIs() {
        return Stream.of(
            Arguments.of("http://guest:guest@localhost:5672/"), // Wrong protocol
            Arguments.of("amqp://user:pass@host:5672/extra/path"),      // Extra path element
            Arguments.of("BAD_URI"));                                   // Just bad
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
    void shouldRespectBeanContract() {
        EqualsVerifier
            .forClass(AmqpUri.class)
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

    @Test
    void toRabbitMqConfigurationShouldMoveTheCommonManagementUriToTheHostOfTheAmqpUri() {
        RabbitMQConfiguration rabbitMQConfiguration = AmqpUri.from("amqp://other:secret@other-rabbitmq:5672/vhost")
            .toRabbitMqConfiguration(COMMON_RABBITMQ)
            .build();

        assertThat(rabbitMQConfiguration.getManagementUri()).isEqualTo(URI.create("https://other-rabbitmq:15672/api"));
        assertThat(rabbitMQConfiguration.getManagementCredentials().getUser()).isEqualTo("other");
    }

    @Test
    void toRabbitMqConfigurationShouldPreserveEscapedCharactersOfTheCommonManagementUri() {
        RabbitMQConfiguration common = commonRabbitMQ("https://common-rabbitmq:15672/team%2Fops/api?a=b%26c#frag%23ment");

        RabbitMQConfiguration rabbitMQConfiguration = AmqpUri.from("amqp://other:secret@other-rabbitmq:5672/vhost")
            .toRabbitMqConfiguration(common)
            .build();

        assertThat(rabbitMQConfiguration.getManagementUri())
            .isEqualTo(URI.create("https://other-rabbitmq:15672/team%2Fops/api?a=b%26c#frag%23ment"));
    }

    @Test
    void toRabbitMqConfigurationShouldUseTheGivenManagementUri() {
        RabbitMQConfiguration rabbitMQConfiguration = AmqpUri.from("amqp://other:secret@other-rabbitmq:5672/vhost")
            .toRabbitMqConfiguration(COMMON_RABBITMQ, Optional.of(URI.create("http://other-management:8080")))
            .build();

        assertThat(rabbitMQConfiguration.getManagementUri()).isEqualTo(URI.create("http://other-management:8080"));
    }

    @Test
    void toRabbitMqConfigurationShouldUseSslForAnAmqpsUri() {
        RabbitMQConfiguration rabbitMQConfiguration = AmqpUri.from("amqps://other:secret@other-rabbitmq:5671/vhost")
            .toRabbitMqConfiguration(COMMON_RABBITMQ)
            .build();

        assertThat(rabbitMQConfiguration.useSsl()).isTrue();
    }

    @Test
    void toRabbitMqConfigurationWithoutCommonConfigurationShouldUseSslForAnAmqpsUri() {
        RabbitMQConfiguration rabbitMQConfiguration = AmqpUri.from("amqps://other:secret@other-rabbitmq:5671/vhost")
            .toRabbitMqConfiguration()
            .build();

        assertThat(rabbitMQConfiguration.useSsl()).isTrue();
    }

    @Test
    void toRabbitMqConfigurationWithoutCommonConfigurationShouldNotUseSslForAnAmqpUri() {
        RabbitMQConfiguration rabbitMQConfiguration = AmqpUri.from("amqp://other:secret@other-rabbitmq:5672/vhost")
            .toRabbitMqConfiguration()
            .build();

        assertThat(rabbitMQConfiguration.useSsl()).isFalse();
    }
}
