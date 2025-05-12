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
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.backends.redis.StandaloneRedisConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RedisStandaloneOidcTokenCacheTest extends RedisOidcTokenCacheContract {
    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    @AfterEach
    void tearDown() {
        if (redisExtension.dockerRedis().isPaused()) {
            redisExtension.dockerRedis().unPause();
        }
    }

    @Override
    public RedisConfiguration redisConfiguration() {
        return StandaloneRedisConfiguration.from(redisExtension.dockerRedis().redisURI().toString());
    }

    @Override
    public void pauseRedis() {
        redisExtension.dockerRedis().pause();
    }
}
