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

import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Preconditions;

/** The {@code teamMailboxEvents.*} keys of {@code space.properties}. */
public record TeamMailboxEventsConfiguration(boolean enabled, String exchange, String receivedRoutingKey, String sentRoutingKey) {
    private static final String ENABLED_PARAMETER = "teamMailboxEvents.enabled";
    private static final String EXCHANGE_PARAMETER = "teamMailboxEvents.exchange";
    private static final String RECEIVED_ROUTING_KEY_PARAMETER = "teamMailboxEvents.receivedRoutingKey";
    private static final String SENT_ROUTING_KEY_PARAMETER = "teamMailboxEvents.sentRoutingKey";
    private static final boolean DEFAULT_ENABLED = false;
    private static final String DEFAULT_EXCHANGE = "tmail";
    private static final String DEFAULT_RECEIVED_ROUTING_KEY = "team-mailbox.message.received";
    private static final String DEFAULT_SENT_ROUTING_KEY = "team-mailbox.message.sent";

    public static final TeamMailboxEventsConfiguration DEFAULT = new TeamMailboxEventsConfiguration(DEFAULT_ENABLED,
        DEFAULT_EXCHANGE, DEFAULT_RECEIVED_ROUTING_KEY, DEFAULT_SENT_ROUTING_KEY);

    public static TeamMailboxEventsConfiguration from(Configuration configuration) {
        return new TeamMailboxEventsConfiguration(
            configuration.getBoolean(ENABLED_PARAMETER, DEFAULT_ENABLED),
            optional(configuration, EXCHANGE_PARAMETER).orElse(DEFAULT_EXCHANGE),
            optional(configuration, RECEIVED_ROUTING_KEY_PARAMETER).orElse(DEFAULT_RECEIVED_ROUTING_KEY),
            optional(configuration, SENT_ROUTING_KEY_PARAMETER).orElse(DEFAULT_SENT_ROUTING_KEY));
    }

    private static Optional<String> optional(Configuration configuration, String parameter) {
        Optional<String> value = Optional.ofNullable(configuration.getString(parameter, null));
        Preconditions.checkArgument(value.map(v -> !v.isBlank()).orElse(true), "'%s' must not be blank", parameter);
        return value;
    }

    public String routingKey(TeamMailboxEvent.Direction direction) {
        return switch (direction) {
            case RECEIVED -> receivedRoutingKey;
            case SENT -> sentRoutingKey;
        };
    }
}
