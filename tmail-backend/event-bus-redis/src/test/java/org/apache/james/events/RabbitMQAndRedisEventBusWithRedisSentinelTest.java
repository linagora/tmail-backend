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

import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.RedisSentinelExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

@Disabled("Redis Sentinel tests are heavy to run")
public class RabbitMQAndRedisEventBusWithRedisSentinelTest extends RabbitMQAndRedisEventBusContractTest {
    @RegisterExtension
    static RedisSentinelExtension redisSentinelExtension = new RedisSentinelExtension();

    @Override
    RedisConfiguration redisConfiguration() {
        return redisSentinelExtension.getRedisSentinelCluster().redisSentinelContainerList().getRedisConfiguration();
    }

    @Override
    void pauseRedis() {
        redisSentinelExtension.getRedisSentinelCluster().redisMasterReplicaContainerList().pauseMasterNode();
    }

    @Override
    void unpauseRedis() {
        redisSentinelExtension.getRedisSentinelCluster().redisMasterReplicaContainerList().unPauseMasterNode();
    }

}
