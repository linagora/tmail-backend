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

package com.linagora.tmail.saas.rabbitmq.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;


public class SaaSSubscriptionRabbitMQConfigurationTest {
    @Test
    void shouldFallbackToDefaultConfig() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        SaaSSubscriptionRabbitMQConfiguration configuration = SaaSSubscriptionRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(configuration).isEqualTo(new SaaSSubscriptionRabbitMQConfiguration(
            "saas.subscription",
            "saas.subscription.routingKey",
            "domain.subscription.changed"));
    }

    @Test
    void configureExchangeShouldWork() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.saas.subscription.exchange", "SaaSSubscriptionExchange");

        SaaSSubscriptionRabbitMQConfiguration configuration = SaaSSubscriptionRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(configuration.exchange()).isEqualTo("SaaSSubscriptionExchange");
    }

    @Test
    void configureRoutingKeyShouldWork() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.saas.subscription.routingKey", "SaaSSubscriptionRoutingKey");

        SaaSSubscriptionRabbitMQConfiguration configuration = SaaSSubscriptionRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(configuration.routingKey()).isEqualTo("SaaSSubscriptionRoutingKey");
    }

    @Test
    void configureDomainRoutingKeyShouldWork() {
        PropertiesConfiguration rabbitConfiguration = new PropertiesConfiguration();
        rabbitConfiguration.addProperty("twp.saas.domain.subscription.routingKey", "SaaSDomainSubscriptionRoutingKey");

        SaaSSubscriptionRabbitMQConfiguration configuration = SaaSSubscriptionRabbitMQConfiguration.from(rabbitConfiguration);

        assertThat(configuration.domainRoutingKey()).isEqualTo("SaaSDomainSubscriptionRoutingKey");
    }
}
