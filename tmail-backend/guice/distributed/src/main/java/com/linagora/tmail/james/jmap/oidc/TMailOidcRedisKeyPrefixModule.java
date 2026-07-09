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
 *******************************************************************/

package com.linagora.tmail.james.jmap.oidc;

import org.apache.james.oidc.redis.RedisOidcTokenCacheKeyPrefix;

import com.google.inject.AbstractModule;

public class TMailOidcRedisKeyPrefixModule extends AbstractModule {
    public static final RedisOidcTokenCacheKeyPrefix TMAIL_REDIS_KEY_PREFIX =
        new RedisOidcTokenCacheKeyPrefix("tmail_oidc_hash_", "tmail_oidc_sid_");

    @Override
    protected void configure() {
        bind(RedisOidcTokenCacheKeyPrefix.class).toInstance(TMAIL_REDIS_KEY_PREFIX);
    }
}
