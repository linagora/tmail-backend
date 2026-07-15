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

package com.linagora.tmail.james.app;

import org.apache.james.backends.redis.RedisExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

public class TmailRedisExtension extends RedisExtension {

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        // RedisExtension uses a static DockerRedis singleton. With reuseForks=true,
        // stopping it in afterAll forces a stop/start cycle between test classes,
        // causing flaky RedisConnectionException. Keep the container alive;
        // testcontainers Ryuk reaps it on JVM exit.
    }
}
