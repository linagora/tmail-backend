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

package com.linagora.tmail.james.jmap.oidc;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;

public record RedisOidcTokenCacheConfiguration(Duration commandTimeout) {
    public static final String COMMAND_TIMEOUT_PROPERTY = "oidc.token.cache.redis.command.timeout";
    public static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(3);
    public static final RedisOidcTokenCacheConfiguration DEFAULT = new RedisOidcTokenCacheConfiguration(DEFAULT_COMMAND_TIMEOUT);

    public static RedisOidcTokenCacheConfiguration from(Configuration configuration) {
        return new RedisOidcTokenCacheConfiguration(
            Optional.ofNullable(configuration.getString(COMMAND_TIMEOUT_PROPERTY, null))
                .map(DurationParser::parse)
                .orElse(DEFAULT_COMMAND_TIMEOUT));
    }

    public RedisOidcTokenCacheConfiguration {
        Preconditions.checkArgument(!commandTimeout.isZero() && !commandTimeout.isNegative(), "commandTimeout must be positive");
    }
}