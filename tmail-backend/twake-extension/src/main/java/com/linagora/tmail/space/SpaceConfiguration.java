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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
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
                                 Duration retention,
                                 Set<String> reservedNames) {
    private static final Pattern PREFIX_PATTERN = Pattern.compile("([a-z0-9_]+-)?");
    // As the Twake Mail side service: leaves room in the 64 characters of a team mailbox name
    private static final int PREFIX_MAX_LENGTH = 32;

    /**
     * The team mailbox names a space never takes, whatever {@code teamMailbox.prefix}: the role addresses of RFC 2142
     * (but its business ones, info, marketing, sales and support, that a team legitimately takes), the addresses a
     * certificate authority sends its domain validation mail to (CA/Browser Forum BR 3.2.2.4.4), the system senders
     * and the team mailbox principals.
     */
    public static final Set<String> DEFAULT_RESERVED_NAMES = ImmutableSet.of(
        "abuse", "noc", "security", "postmaster", "hostmaster", "usenet", "news", "webmaster", "www", "uucp", "ftp",
        "admin", "administrator",
        "mailer-daemon", "noreply", "no-reply", "root",
        "team-mailbox", "team-mailbox-admin");

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
            optional(configuration, "retention").map(value -> DurationParser.parse(value, ChronoUnit.DAYS)).orElse(DEFAULT.retention()),
            reservedNames(configuration));
    }

    private static Set<String> reservedNames(Configuration configuration) {
        return ImmutableSet.<String>builder()
            .addAll(DEFAULT_RESERVED_NAMES)
            .addAll(configuration.getList(String.class, "teamMailbox.reservedNames", List.of()).stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .iterator())
            .build();
    }

    /** With the {@link #DEFAULT_RESERVED_NAMES} only. */
    public SpaceConfiguration(Optional<AmqpUri> rabbitMQUri, Optional<URI> rabbitMQManagementUri, String queue,
                              String deadLetterQueue, String spaceExchange, String dnsExchange, String dnsRoutingKey,
                              String activityExchange, String teamMailboxPrefix, Duration retention) {
        this(rabbitMQUri, rabbitMQManagementUri, queue, deadLetterQueue, spaceExchange, dnsExchange, dnsRoutingKey,
            activityExchange, teamMailboxPrefix, retention, DEFAULT_RESERVED_NAMES);
    }

    public SpaceConfiguration {
        reservedNames = ImmutableSet.copyOf(reservedNames);
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
            .map(amqpUri -> amqpUri.toRabbitMqConfiguration(tmailRabbitMQConfiguration, rabbitMQManagementUri).build())
            .orElse(tmailRabbitMQConfiguration);
    }

    private static Optional<String> optional(Configuration configuration, String parameter) {
        Optional<String> value = Optional.ofNullable(configuration.getString(parameter, null));
        Preconditions.checkArgument(value.map(v -> !v.isBlank()).orElse(true), "'%s' must not be blank", parameter);
        return value;
    }
}
