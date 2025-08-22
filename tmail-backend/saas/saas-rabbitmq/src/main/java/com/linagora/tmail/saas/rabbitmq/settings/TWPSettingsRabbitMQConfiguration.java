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

package com.linagora.tmail.saas.rabbitmq.settings;

import org.apache.commons.configuration2.Configuration;

public record TWPSettingsRabbitMQConfiguration(String exchange,
                                               String routingKey) {
    private static final String TWP_SETTINGS_EXCHANGE_PROPERTY = "twp.settings.exchange";
    private static final String TWP_SETTINGS_ROUTING_KEY_PROPERTY = "twp.settings.routingKey";
    public static final String TWP_SETTINGS_EXCHANGE_DEFAULT = "settings";
    public static final String TWP_SETTINGS_ROUTING_KEY_DEFAULT = "user.settings.updated";

    public static TWPSettingsRabbitMQConfiguration from(Configuration rabbitMQConfiguration) {
        String exchange = rabbitMQConfiguration.getString(TWP_SETTINGS_EXCHANGE_PROPERTY, TWP_SETTINGS_EXCHANGE_DEFAULT);
        String routingKey = rabbitMQConfiguration.getString(TWP_SETTINGS_ROUTING_KEY_PROPERTY, TWP_SETTINGS_ROUTING_KEY_DEFAULT);

        return new TWPSettingsRabbitMQConfiguration(exchange, routingKey);
    }
}
