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

import org.apache.james.backends.redis.RedisClusterExtension;
import org.apache.james.backends.redis.RedisConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RedisClusterOidcTokenCacheTest extends RedisOidcTokenCacheContract {
    @RegisterExtension
    static RedisClusterExtension redisClusterExtension = new RedisClusterExtension();

    private static RedisClusterExtension.RedisClusterContainer redisClusterContainer;

    @BeforeAll
    static void setUp(RedisClusterExtension.RedisClusterContainer container) {
        redisClusterContainer = container;
    }

    @AfterEach
    void tearDown() {
        redisClusterContainer.unPauseOne();
    }

    @Override
    public RedisConfiguration redisConfiguration() {
        return redisClusterContainer.getRedisConfiguration();
    }

    @Override
    public void pauseRedis() {
        redisClusterContainer.unPauseOne();
    }
}
