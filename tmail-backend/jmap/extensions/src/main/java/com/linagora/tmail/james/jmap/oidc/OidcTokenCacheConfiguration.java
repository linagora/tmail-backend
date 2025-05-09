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

public record OidcTokenCacheConfiguration(Duration expiration, Optional<Long> tokenCacheMaxSize) {

    public static final String OIDC_TOKEN_CACHE_EXPIRATION = "oidc.token.cache.expiration";
    public static final String OIDC_TOKEN_CACHE_MAX_SIZE = "oidc.token.cache.maxSize";
    public static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(5);

    public static final OidcTokenCacheConfiguration DEFAULT  = new OidcTokenCacheConfiguration(DEFAULT_EXPIRATION, Optional.empty());

    public static OidcTokenCacheConfiguration parse(Configuration configuration) {
        Optional<Long> tokenCacheMaxSize = Optional.ofNullable(configuration.getString(OIDC_TOKEN_CACHE_MAX_SIZE, null))
            .map(Long::parseLong);

        return new OidcTokenCacheConfiguration(Optional.ofNullable(configuration.getString(OIDC_TOKEN_CACHE_EXPIRATION))
            .map(DurationParser::parse).orElse(DEFAULT_EXPIRATION), tokenCacheMaxSize);
    }

    public OidcTokenCacheConfiguration {
        tokenCacheMaxSize.ifPresent(maxSize -> Preconditions.checkArgument(maxSize > 0, "maxSize must be greater than 0"));
    }
}
