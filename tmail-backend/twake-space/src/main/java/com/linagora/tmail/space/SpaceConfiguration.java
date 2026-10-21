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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;
import com.linagora.tmail.AmqpUri;

/**
 * Read from {@code space.properties}, once {@link SpaceModuleChooserConfiguration} enabled the extension. Every
 * setting has a default.
 */
public record SpaceConfiguration(Optional<AmqpUri> rabbitMQUri,
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

    public static final SpaceConfiguration DEFAULT = new SpaceConfiguration(Optional.empty(),
        "tmail-space", "tmail-space-dead-letter", "space", "admin-panel", "dns.validated", "activity", "",
        Duration.ofDays(30));

    public static SpaceConfiguration from(Configuration configuration) {
        return new SpaceConfiguration(
            optional(configuration, "rabbitmq.uri").map(AmqpUri::from),
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
    }

    private static Optional<String> optional(Configuration configuration, String parameter) {
        Optional<String> value = Optional.ofNullable(configuration.getString(parameter, null));
        Preconditions.checkArgument(value.map(v -> !v.isBlank()).orElse(true), "'%s' must not be blank", parameter);
        return value;
    }
}
