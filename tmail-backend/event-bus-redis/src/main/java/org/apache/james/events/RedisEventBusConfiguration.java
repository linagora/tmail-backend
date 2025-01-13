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

package org.apache.james.events;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;

public record RedisEventBusConfiguration(boolean failureIgnore, Duration durationTimeout) {
    public static final boolean FAILURE_IGNORE_DEFAULT = false;
    public static final Duration DURATION_TIMEOUT_DEFAULT = Duration.ofSeconds(10);
    public static final RedisEventBusConfiguration DEFAULT = new RedisEventBusConfiguration(FAILURE_IGNORE_DEFAULT, DURATION_TIMEOUT_DEFAULT);

    public static RedisEventBusConfiguration from(Configuration configuration) {
        return new RedisEventBusConfiguration(
            configuration.getBoolean("eventBus.redis.failure.ignore", FAILURE_IGNORE_DEFAULT),
            Optional.ofNullable(configuration.getString("eventBus.redis.timeout"))
                .map(DurationParser::parse)
                .orElse(DURATION_TIMEOUT_DEFAULT));
    }
}
