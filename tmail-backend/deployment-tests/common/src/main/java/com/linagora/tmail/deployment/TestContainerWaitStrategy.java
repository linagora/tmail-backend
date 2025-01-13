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

package com.linagora.tmail.deployment;

import java.time.Duration;

import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

public class TestContainerWaitStrategy extends LogMessageWaitStrategy {
    public static final Duration STARTUP_TIMEOUT_DURATION = Duration.ofMinutes(3);

    public static final WaitStrategy WAIT_STRATEGY =  new LogMessageWaitStrategy().withRegEx(".*JAMES server started.*\\n").withTimes(1)
                .withStartupTimeout(STARTUP_TIMEOUT_DURATION);
}
