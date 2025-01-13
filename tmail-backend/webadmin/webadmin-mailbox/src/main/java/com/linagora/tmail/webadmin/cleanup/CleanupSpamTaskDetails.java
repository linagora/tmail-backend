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

import java.time.Instant;

import org.apache.james.task.TaskExecutionDetails;

import com.google.common.collect.ImmutableList;

public record CleanupSpamTaskDetails(Instant instant,
                                     long processedUsersCount,
                                     long deletedMessagesCount,
                                     ImmutableList<String> failedUsers,
                                     RunningOptions runningOptions) implements TaskExecutionDetails.AdditionalInformation {
    @Override
    public Instant timestamp() {
        return instant;
    }
}
