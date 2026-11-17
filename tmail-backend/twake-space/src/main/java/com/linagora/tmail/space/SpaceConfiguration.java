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

package com.linagora.tmail.space;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;
import com.linagora.tmail.AmqpUri;

/**
 * Read from {@code space.properties}, once {@link SpaceModuleChooserConfiguration} enabled the extension. Every
 * setting has a default.
 */
public record SpaceConfiguration(Optional<AmqpUri> rabbitMQUri,
                                 Optional<URI> rabbitMQManagementUri,
                                 String queue,
                                 String deadLetterQueue,
                                 String spaceExchange,
                                 String dnsExchange,
                                 String dnsRoutingKey,
                                 String activityExchange,
                                 String teamMailboxPrefix,
                                 Duration retention) {
    private static final Pattern PREFIX_PATTERN = Pattern.compile("([a-z0-9_]+-)?");
    // As the Twake Mail side service: leaves room in the 64 characters of a team mailbox name
    private static final int PREFIX_MAX_LENGTH = 32;

    public static final SpaceConfiguration DEFAULT = new SpaceConfiguration(Optional.empty(), Optional.empty(),
        "tmail-space", "tmail-space-dead-letter", "space", "admin-panel", "dns.validated", "activity", "",
        Duration.ofDays(30));

    public static SpaceConfiguration from(Configuration configuration) {
        return new SpaceConfiguration(
            optional(configuration, "rabbitmq.uri").map(AmqpUri::from),
            optional(configuration, "rabbitmq.management.uri").map(URI::create),
            optional(configuration, "queue").orElse(DEFAULT.queue()),
            optional(configuration, "deadLetterQueue").orElse(DEFAULT.deadLetterQueue()),
            optional(configuration, "space.exchange").orElse(DEFAULT.spaceExchange()),
            optional(configuration, "dns.exchange").orElse(DEFAULT.dnsExchange()),
            optional(configuration, "dns.routingKey").orElse(DEFAULT.dnsRoutingKey()),
            optional(configuration, "activity.exchange").orElse(DEFAULT.activityExchange()),
            Optional.ofNullable(configuration.getString("teamMailbox.prefix", null)).orElse(DEFAULT.teamMailboxPrefix()),
            optional(configuration, "retention").map(value -> DurationParser.parse(value, ChronoUnit.DAYS)).orElse(DEFAULT.retention()));
    }

    public SpaceConfiguration {
        Preconditions.checkArgument(PREFIX_PATTERN.matcher(teamMailboxPrefix).matches(),
            "'teamMailbox.prefix' must be lowercase letters, digits or '_', ending with a dash");
        Preconditions.checkArgument(teamMailboxPrefix.length() <= PREFIX_MAX_LENGTH,
            "'teamMailbox.prefix' must be at most %s characters", PREFIX_MAX_LENGTH);
        Preconditions.checkArgument(!retention.isNegative(), "'retention' must not be negative");
        Preconditions.checkArgument(rabbitMQUri.map(amqpUri -> amqpUri.getUri().getHost() != null).orElse(true),
            "'rabbitmq.uri' must specify a host");
    }

    /**
     * The RabbitMQ server of the extension: the one of {@code rabbitmq.uri}, its vhost included, with the other
     * settings of {@code rabbitmq.properties}. Without {@code rabbitmq.uri}, the server of {@code rabbitmq.properties}.
     */
    public RabbitMQConfiguration rabbitMQConfiguration(RabbitMQConfiguration tmailRabbitMQConfiguration) {
        return rabbitMQUri
            .map(amqpUri -> amqpUri.toRabbitMqConfiguration(tmailRabbitMQConfiguration,
                    Optional.of(managementUri(amqpUri, tmailRabbitMQConfiguration)))
                .build())
            .orElse(tmailRabbitMQConfiguration);
    }

    // The management API is called with the credentials of rabbitmq.uri: it must be the one of that server. Without
    // rabbitmq.management.uri, the management URI of rabbitmq.properties moved to the host of rabbitmq.uri.
    private URI managementUri(AmqpUri amqpUri, RabbitMQConfiguration tmailRabbitMQConfiguration) {
        return rabbitMQManagementUri
            .orElseGet(() -> withHost(tmailRabbitMQConfiguration.getManagementUri(), amqpUri.getUri().getHost()));
    }

    // Drops the user information, which would belong to the server of rabbitmq.properties. Keeps the raw path, query
    // and fragment, so that their escaped characters stay escaped.
    private static URI withHost(URI uri, String host) {
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        String path = Optional.ofNullable(uri.getRawPath()).orElse("");
        String query = Optional.ofNullable(uri.getRawQuery()).map(rawQuery -> "?" + rawQuery).orElse("");
        String fragment = Optional.ofNullable(uri.getRawFragment()).map(rawFragment -> "#" + rawFragment).orElse("");
        try {
            return new URI(uri.getScheme() + "://" + host + port + path + query + fragment);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot build the management URI of host " + host, e);
        }
    }

    private static Optional<String> optional(Configuration configuration, String parameter) {
        Optional<String> value = Optional.ofNullable(configuration.getString(parameter, null));
        Preconditions.checkArgument(value.map(v -> !v.isBlank()).orElse(true), "'%s' must not be blank", parameter);
        return value;
    }
}
