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

package com.linagora.tmail.james.jmap;

import java.net.URI;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public record RabbitMQEmailAddressContactConfiguration(String queueName, URI amqpUri,
                                                       RabbitMQConfiguration.ManagementCredentials managementCredentials,
                                                       Optional<String> vhost) {
    static final String DEAD_LETTER_EXCHANGE_PREFIX = "TmailQueue-dead-letter-exchange-";
    static final String DEAD_LETTER_QUEUE_PREFIX = "TmailQueue-dead-letter-queue-";
    static final String AQMP_URI_PROPERTY = "address.contact.uri";
    static final String AQMP_MANAGEMENT_USER = "address.contact.user";
    static final String AQMP_MANAGEMENT_PASSWORD = "address.contact.password";
    static final String AQMP_QUEUE_NAME_PROPERTY = "address.contact.queue";
    static final String AQMP_EXCHANGE_PREFIX = "TmailExchange-";
    static final String VHOST = "vhost";

    public static RabbitMQEmailAddressContactConfiguration from(Configuration configuration) {
        String aqmpURIAsString = configuration.getString(AQMP_URI_PROPERTY);
        Preconditions.checkState(!Strings.isNullOrEmpty(aqmpURIAsString),
            String.format("You need to specify the URI of RabbitMQ by '%s' property", AQMP_URI_PROPERTY));
        URI aqmpUri = URI.create(aqmpURIAsString);

        Preconditions.checkState(CharMatcher.is('/').countIn(aqmpUri.getPath()) <= 1,
            String.format("RabbitMQ URI Specification invalid '%s'", aqmpUri.toASCIIString()));

        String managementUser = configuration.getString(AQMP_MANAGEMENT_USER);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementUser),
            String.format("You need to specify the %s property as username of rabbitmq account", AQMP_MANAGEMENT_USER));
        String managementPassword = configuration.getString(AQMP_MANAGEMENT_PASSWORD);
        Preconditions.checkState(!Strings.isNullOrEmpty(managementPassword),
            String.format("You need to specify the %s property as password of rabbitmq account", AQMP_MANAGEMENT_PASSWORD));
        RabbitMQConfiguration.ManagementCredentials managementCredential = new RabbitMQConfiguration.ManagementCredentials(managementUser, managementPassword.toCharArray());

        String queueName = configuration.getString(AQMP_QUEUE_NAME_PROPERTY);
        Preconditions.checkState(!Strings.isNullOrEmpty(queueName),
            String.format("You need to specify the %s property as queue name for contact address feature", AQMP_QUEUE_NAME_PROPERTY));

        Optional<String> vhost = Optional.ofNullable(configuration.getString(VHOST, null));

        return new RabbitMQEmailAddressContactConfiguration(queueName, aqmpUri, managementCredential, vhost);
    }

    public String getExchangeName() {
        return AQMP_EXCHANGE_PREFIX + queueName;
    }

    public String getDeadLetterExchange() {
        return DEAD_LETTER_EXCHANGE_PREFIX + queueName;
    }

    public String getDeadLetterQueue() {
        return DEAD_LETTER_QUEUE_PREFIX + queueName;
    }

    public Optional<String> vhost() {
        return vhost.or(this::getVhostFromPath);
    }

    private Optional<String> getVhostFromPath() {
        String vhostPath = amqpUri.getPath();
        if (vhostPath.startsWith("/")) {
            return Optional.of(vhostPath.substring(1))
                .filter(value -> !value.isEmpty());
        }
        return Optional.empty();
    }
}
