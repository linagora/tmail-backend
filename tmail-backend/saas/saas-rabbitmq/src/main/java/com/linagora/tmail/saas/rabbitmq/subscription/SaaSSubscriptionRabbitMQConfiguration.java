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
                                                    String routingKey) {
    private static final String TWP_SAAS_SUBSCRIPTION_EXCHANGE_PROPERTY = "twp.saas.subscription.exchange";
    private static final String TWP_SAAS_SUBSCRIPTION_ROUTING_KEY_PROPERTY = "twp.saas.subscription.routingKey";
    public static final String TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT = "saas.subscription";
    public static final String TWP_SAAS_SUBSCRIPTION_ROUTING_KEY_DEFAULT = "saas.subscription.routingKey";

    public static SaaSSubscriptionRabbitMQConfiguration from(Configuration rabbitMQConfiguration) {
        String exchange = rabbitMQConfiguration.getString(TWP_SAAS_SUBSCRIPTION_EXCHANGE_PROPERTY, TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT);
        String routingKey = rabbitMQConfiguration.getString(TWP_SAAS_SUBSCRIPTION_ROUTING_KEY_PROPERTY, TWP_SAAS_SUBSCRIPTION_ROUTING_KEY_DEFAULT);

        return new SaaSSubscriptionRabbitMQConfiguration(exchange, routingKey);
    }
}
