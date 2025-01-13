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

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;

import com.google.common.base.Preconditions;

public record AmqpUserInfo(String username, String password) {
    public AmqpUserInfo {
        Preconditions.checkNotNull(username, "Amqp username is required.");
        Preconditions.checkNotNull(password, "Amqp password is required.");
    }

    public RabbitMQConfiguration.ManagementCredentials asManagementCredentials() {
        return new RabbitMQConfiguration.ManagementCredentials(username, password.toCharArray());
    }
}
