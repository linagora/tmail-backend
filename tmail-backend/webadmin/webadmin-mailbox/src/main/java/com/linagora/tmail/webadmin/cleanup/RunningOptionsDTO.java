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

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RunningOptionsDTO(@JsonProperty("usersPerSecond") Optional<Integer> usersPerSecond) {

    public static RunningOptionsDTO asDTO(RunningOptions runningOptions) {
        return new RunningOptionsDTO(Optional.of(runningOptions.getUsersPerSecond()));
    }

    public RunningOptions asDomainObject() {
        return RunningOptions.of(usersPerSecond.orElse(RunningOptions.DEFAULT_USERS_PER_SECOND));
    }

}
