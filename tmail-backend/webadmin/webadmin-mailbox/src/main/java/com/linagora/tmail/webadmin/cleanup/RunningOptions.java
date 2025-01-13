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

package com.linagora.tmail.webadmin.cleanup;

import com.google.common.base.Preconditions;

public class RunningOptions {
    public static final int DEFAULT_USERS_PER_SECOND = 1;
    public static final RunningOptions DEFAULT = of(DEFAULT_USERS_PER_SECOND);

    public static RunningOptions of(int usersPerSecond) {
        return new RunningOptions(usersPerSecond);
    }

    private final int usersPerSecond;

    private RunningOptions(int usersPerSecond) {
        Preconditions.checkArgument(usersPerSecond > 0, "'usersPerSecond' needs to be strictly positive");

        this.usersPerSecond = usersPerSecond;
    }

    public int getUsersPerSecond() {
        return usersPerSecond;
    }
}
