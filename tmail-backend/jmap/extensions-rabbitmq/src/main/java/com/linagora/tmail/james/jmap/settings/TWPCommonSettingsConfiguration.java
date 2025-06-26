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

package com.linagora.tmail.james.jmap.settings;

import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.AmqpUri;

public record TWPCommonSettingsConfiguration(boolean enabled,
                                             Optional<List<AmqpUri>> amqpUri,
                                             boolean quorumQueuesBypass,
                                             String exchange,
                                             String routingKey) {
    private static final String TWP_RABBITMQ_URI_PROPERTY = "twp.rabbitmq.uri";
    private static final String TWP_QUEUES_QUORUM_BYPASS_PROPERTY = "twp.queues.quorum.bypass";
    private static final String TWP_SETTINGS_EXCHANGE_PROPERTY = "twp.settings.exchange";
    private static final String TWP_SETTINGS_ROUTING_KEY_PROPERTY = "twp.settings.routingKey";
    public static final boolean TWP_COMMON_SETTINGS_DISABLED = false;
    public static final boolean TWP_QUEUES_QUORUM_BYPASS_DISABLED = false;
    private static final String TWP_SETTINGS_EXCHANGE_DEFAULT = "settings";
    private static final String TWP_SETTINGS_ROUTING_KEY_DEFAULT = "user.settings.updated";

    public static TWPCommonSettingsConfiguration from(Configuration jmapConfiguration, Configuration rabbitMQConfiguration) {
        boolean enabled = Optional.ofNullable(jmapConfiguration.getString("settings.readonly.properties.providers", null))
            .map(value -> value.contains(TWPReadOnlyPropertyProvider.class.getSimpleName()))
            .orElse(TWP_COMMON_SETTINGS_DISABLED);
        boolean quorumQueuesBypass = rabbitMQConfiguration.getBoolean(TWP_QUEUES_QUORUM_BYPASS_PROPERTY, TWP_QUEUES_QUORUM_BYPASS_DISABLED);
        String exchange = rabbitMQConfiguration.getString(TWP_SETTINGS_EXCHANGE_PROPERTY, TWP_SETTINGS_EXCHANGE_DEFAULT);
        String routingKey = rabbitMQConfiguration.getString(TWP_SETTINGS_ROUTING_KEY_PROPERTY, TWP_SETTINGS_ROUTING_KEY_DEFAULT);

        return new TWPCommonSettingsConfiguration(enabled, readTWPRabbitMqUris(rabbitMQConfiguration), quorumQueuesBypass, exchange, routingKey);
    }

    private static Optional<List<AmqpUri>> readTWPRabbitMqUris(Configuration rabbitConfiguration) {
        String rabbitMqUri = rabbitConfiguration.getString(TWP_RABBITMQ_URI_PROPERTY);
        if (StringUtils.isBlank(rabbitMqUri)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Splitter.on(',').trimResults()
                .splitToStream(rabbitMqUri)
                .map(AmqpUri::from)
                .collect(ImmutableList.toImmutableList()));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid Twake RabbitMQ URI in rabbitmq.properties");
        }
    }
}
