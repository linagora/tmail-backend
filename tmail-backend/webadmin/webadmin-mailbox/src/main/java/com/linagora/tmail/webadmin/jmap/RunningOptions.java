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

import com.google.common.base.Preconditions;

public record RunningOptions(int messagesPerSecond) {
    public static final RunningOptions DEFAULT = new RunningOptions(500);

    public static RunningOptions withMessageRatePerSecond(int messageRatePerSecond) {
        return new RunningOptions(messageRatePerSecond);
    }

    public RunningOptions {
        Preconditions.checkArgument(messagesPerSecond > 0, "'messagesPerSecond' must be strictly positive");
    }
}
