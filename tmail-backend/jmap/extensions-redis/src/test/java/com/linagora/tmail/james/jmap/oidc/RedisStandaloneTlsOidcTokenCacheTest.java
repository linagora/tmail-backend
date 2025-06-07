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

import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.RedisTLSExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RedisStandaloneTlsOidcTokenCacheTest extends RedisOidcTokenCacheContract {
    @RegisterExtension
    static RedisTLSExtension redisExtension = new RedisTLSExtension();

    @AfterEach
    void tearDown() {
        redisExtension.getRedisContainer().unPause();
    }

    @Override
    public RedisConfiguration redisConfiguration() {
        return redisExtension.getRedisContainer().getConfiguration();
    }

    @Override
    public void pauseRedis() {
        redisExtension.getRedisContainer().pause();
    }
}
