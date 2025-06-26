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

import static com.linagora.tmail.james.jmap.settings.TWPCommonSettingsConfiguration.TWP_COMMON_SETTINGS_DISABLED;
import static com.linagora.tmail.james.jmap.settings.TWPCommonSettingsConfiguration.TWP_QUEUES_QUORUM_BYPASS_DISABLED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.AmqpUri;

public class TWPCommonSettingsConfigurationTest {
    @Test
    void twpCommonSettingsShouldBeDisabledByDefault() {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(jmapConfiguration, rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration).isEqualTo(new TWPCommonSettingsConfiguration(TWP_COMMON_SETTINGS_DISABLED,
            Optional.empty(),
            TWP_QUEUES_QUORUM_BYPASS_DISABLED,
            "settings",
            "user.settings.updated"));
    }

    @Test
    void twpCommonSettingsShouldBeDisabledWhenTWPReadOnlyPropertyProviderNotConfigured() {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        jmapConfiguration.addProperty("settings.readonly.properties.providers", "com.linagora.tmail.james.jmap.settings.FixedLanguageReadOnlyPropertyProvider");
        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(jmapConfiguration, rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration.enabled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "TWPReadOnlyPropertyProvider",
        "com.linagora.tmail.james.jmap.settings.TWPReadOnlyPropertyProvider",
        "com.linagora.tmail.james.jmap.settings.TWPReadOnlyPropertyProvider, com.linagora.tmail.james.jmap.settings.FixedLanguageReadOnlyPropertyProvider"})
    void twpCommonSettingsShouldBeEnabledWhenTWPReadOnlyPropertyProviderConfigured(String readOnlyPropertyProviders) {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        jmapConfiguration.addProperty("settings.readonly.properties.providers", readOnlyPropertyProviders);
        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(jmapConfiguration, rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration.enabled()).isTrue();
    }

    @Test
    void parsingAmqpUrisShouldSucceedWhenValidTWPRabbitMqUri() {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.rabbitmq.uri", "amqp://james:password@rabbitmqhost:5672/twp-vhost");

        AmqpUri amqpUri = TWPCommonSettingsConfiguration.from(jmapConfiguration, rabbitConfiguration)
            .amqpUri().get().getFirst();

        assertThat(amqpUri.getUri().toString()).isEqualTo("amqp://james:password@rabbitmqhost:5672/twp-vhost");
        assertThat(amqpUri.getUserInfo().username()).isEqualTo("james");
        assertThat(amqpUri.getUserInfo().password()).isEqualTo("password");
        assertThat(amqpUri.getVhost()).contains("twp-vhost");
        assertThat(amqpUri.getPort()).isEqualTo(5672);
    }

    @Test
    void amqpUrisShouldBeEmptyWhenTWPRabbitMqUriIsBlank() {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.rabbitmq.uri", "  ");

        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(jmapConfiguration, rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration.amqpUri()).isEmpty();
    }

    @Test
    void parsingAmqpUrisShouldThrowWhenTWPRabbitMqUriIsInvalid() {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.rabbitmq.uri", "BAD_SCHEME://james:james@rabbitmqhost:5672");

        assertThatThrownBy(() -> TWPCommonSettingsConfiguration.from(jmapConfiguration, rabbitConfiguration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid Twake RabbitMQ URI in rabbitmq.properties");
    }

    @Test
    void quorumQueuesByPassShouldBeEnabledWhenConfigured() {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.queues.quorum.bypass", "true");

        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(jmapConfiguration, rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration.quorumQueuesBypass()).isTrue();
    }

    @Test
    void configuringExchangeShouldWork() {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.settings.exchange", "TWPSettingsExchange");

        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(jmapConfiguration, rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration.exchange()).isEqualTo("TWPSettingsExchange");
    }

    @Test
    void configuringRoutingKeyShouldWork() {
        PropertiesConfiguration jmapConfiguration = new PropertiesConfiguration();
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.settings.routingKey", "TWPSettingsRoutingKey");

        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = TWPCommonSettingsConfiguration.from(jmapConfiguration, rabbitConfiguration);

        assertThat(twpCommonSettingsConfiguration.routingKey()).isEqualTo("TWPSettingsRoutingKey");
    }

}
