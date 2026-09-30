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

package com.linagora.tmail.saas.rabbitmq.deletion;

import org.apache.commons.configuration2.Configuration;

public record TWPUserDeletionRabbitMQConfiguration(String b2cExchange,
                                                   String b2cRoutingKey,
                                                   String b2bExchange,
                                                   String b2bRoutingKey) {
    private static final String TWP_USER_DELETION_B2C_EXCHANGE_PROPERTY = "twp.user.deletion.b2c.exchange";
    private static final String TWP_USER_DELETION_B2C_ROUTING_KEY_PROPERTY = "twp.user.deletion.b2c.routingKey";
    private static final String TWP_USER_DELETION_B2B_EXCHANGE_PROPERTY = "twp.user.deletion.b2b.exchange";
    private static final String TWP_USER_DELETION_B2B_ROUTING_KEY_PROPERTY = "twp.user.deletion.b2b.routingKey";
    public static final String TWP_USER_DELETION_B2C_EXCHANGE_DEFAULT = "auth";
    public static final String TWP_USER_DELETION_B2C_ROUTING_KEY_DEFAULT = "user.deleted";
    public static final String TWP_USER_DELETION_B2B_EXCHANGE_DEFAULT = "b2b";
    public static final String TWP_USER_DELETION_B2B_ROUTING_KEY_DEFAULT = "domain.user.deleted";

    public static TWPUserDeletionRabbitMQConfiguration from(Configuration rabbitMQConfiguration) {
        return new TWPUserDeletionRabbitMQConfiguration(
            rabbitMQConfiguration.getString(TWP_USER_DELETION_B2C_EXCHANGE_PROPERTY, TWP_USER_DELETION_B2C_EXCHANGE_DEFAULT),
            rabbitMQConfiguration.getString(TWP_USER_DELETION_B2C_ROUTING_KEY_PROPERTY, TWP_USER_DELETION_B2C_ROUTING_KEY_DEFAULT),
            rabbitMQConfiguration.getString(TWP_USER_DELETION_B2B_EXCHANGE_PROPERTY, TWP_USER_DELETION_B2B_EXCHANGE_DEFAULT),
            rabbitMQConfiguration.getString(TWP_USER_DELETION_B2B_ROUTING_KEY_PROPERTY, TWP_USER_DELETION_B2B_ROUTING_KEY_DEFAULT));
    }
}
