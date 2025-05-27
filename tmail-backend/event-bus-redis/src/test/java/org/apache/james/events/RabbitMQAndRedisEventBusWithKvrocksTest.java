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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package org.apache.james.events;

import org.apache.james.backends.redis.KvrocksExtension;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.StandaloneRedisConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

@Disabled("event bus tests are heavy")
public class RabbitMQAndRedisEventBusWithKvrocksTest extends RabbitMQAndRedisEventBusContractTest {
    @RegisterExtension
    static KvrocksExtension kvrocksExtension = new KvrocksExtension();

    @Override
    RedisConfiguration redisConfiguration() {
        return StandaloneRedisConfiguration.from(kvrocksExtension.dockerKvrocks().redisURI().toString());
    }

    @Override
    void pauseRedis() {
        kvrocksExtension.dockerKvrocks().pause();
    }

    @Override
    void unpauseRedis() {
        kvrocksExtension.dockerKvrocks().unPause();
    }
}
