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

package com.linagora.tmail.saas.rabbitmq.subscription;

import org.apache.commons.configuration2.Configuration;

public record SaaSSubscriptionRabbitMQConfiguration(String exchange,
                                                    String routingKey,
                                                    String domainRoutingKey,
                                                    String configurationExchange,
                                                    String domainConfigurationRoutingKey) {

    private static final String TWP_SAAS_SUBSCRIPTION_EXCHANGE_PROPERTY = "twp.saas.subscription.exchange";
    private static final String TWP_SAAS_SUBSCRIPTION_ROUTING_KEY_PROPERTY = "twp.saas.subscription.routingKey";
    private static final String TWP_SAAS_DOMAIN_SUBSCRIPTION_ROUTING_KEY_PROPERTY = "twp.saas.domain.subscription.routingKey";
    public static final String TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT = "saas.subscription";
    public static final String TWP_SAAS_SUBSCRIPTION_ROUTING_KEY_DEFAULT = "saas.subscription.routingKey";
    public static final String TWP_SAAS_DOMAIN_SUBSCRIPTION_ROUTING_KEY_DEFAULT = "domain.subscription.changed";
    public static final String TWP_SAAS_CONFIGURATION_EXCHANGE_PROPERTY = "twp.saas.configuration.exchange";
    public static final String TWP_SAAS_CONFIGURATION_EXCHANGE_DEFAULT = "configuration";
    public static final String TWP_SAAS_DOMAIN_CONFIGURATION_ROUTING_KEY_PROPERTY = "twp.saas.configuration.routingKey";
    public static final String TWP_SAAS_DOMAIN_CONFIGURATION_ROUTING_KEY_DEFAULT = "domain.dns.configuration.status";

    public static final SaaSSubscriptionRabbitMQConfiguration DEFAULT = new SaaSSubscriptionRabbitMQConfiguration(TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT,
        TWP_SAAS_SUBSCRIPTION_ROUTING_KEY_DEFAULT,
        TWP_SAAS_DOMAIN_SUBSCRIPTION_ROUTING_KEY_DEFAULT,
        TWP_SAAS_CONFIGURATION_EXCHANGE_DEFAULT,
        TWP_SAAS_DOMAIN_CONFIGURATION_ROUTING_KEY_DEFAULT);

    public static SaaSSubscriptionRabbitMQConfiguration from(Configuration rabbitMQConfiguration) {
        String exchange = rabbitMQConfiguration.getString(TWP_SAAS_SUBSCRIPTION_EXCHANGE_PROPERTY, TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT);
        String routingKey = rabbitMQConfiguration.getString(TWP_SAAS_SUBSCRIPTION_ROUTING_KEY_PROPERTY, TWP_SAAS_SUBSCRIPTION_ROUTING_KEY_DEFAULT);
        String domainRoutingKey = rabbitMQConfiguration.getString(TWP_SAAS_DOMAIN_SUBSCRIPTION_ROUTING_KEY_PROPERTY, TWP_SAAS_DOMAIN_SUBSCRIPTION_ROUTING_KEY_DEFAULT);
        String configurationExchange = rabbitMQConfiguration.getString(TWP_SAAS_CONFIGURATION_EXCHANGE_PROPERTY, TWP_SAAS_CONFIGURATION_EXCHANGE_DEFAULT);
        String configurationRoutingKey = rabbitMQConfiguration.getString(TWP_SAAS_DOMAIN_CONFIGURATION_ROUTING_KEY_PROPERTY, TWP_SAAS_DOMAIN_CONFIGURATION_ROUTING_KEY_DEFAULT);

        return new SaaSSubscriptionRabbitMQConfiguration(exchange, routingKey, domainRoutingKey, configurationExchange, configurationRoutingKey);
    }
}
