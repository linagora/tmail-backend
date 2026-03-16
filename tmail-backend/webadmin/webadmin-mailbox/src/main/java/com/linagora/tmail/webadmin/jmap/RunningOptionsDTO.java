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

package com.linagora.tmail.webadmin.jmap;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RunningOptionsDTO(Optional<Integer> messagesPerSecond) {
    public static RunningOptionsDTO asDTO(RunningOptions domainObject) {
        return new RunningOptionsDTO(Optional.of(domainObject.messagesPerSecond()));
    }

    @JsonCreator
    public RunningOptionsDTO(@JsonProperty("messagesPerSecond") Optional<Integer> messagesPerSecond) {
        this.messagesPerSecond = messagesPerSecond;
    }

    public RunningOptions asDomainObject() {
        return messagesPerSecond.map(RunningOptions::withMessageRatePerSecond)
            .orElse(RunningOptions.DEFAULT);
    }
}
