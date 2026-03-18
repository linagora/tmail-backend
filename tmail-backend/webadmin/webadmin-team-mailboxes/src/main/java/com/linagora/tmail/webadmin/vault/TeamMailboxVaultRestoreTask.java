/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                  *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.webadmin.vault;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.vault.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.team.TeamMailbox;

public class TeamMailboxVaultRestoreTask implements Task {

    public static final TaskType TYPE = TaskType.of("team-mailbox-deleted-messages-restore");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final String teamMailboxAddress;
        private final long successfulRestoreCount;
        private final long errorRestoreCount;
        private final Instant timestamp;

        public AdditionalInformation(String teamMailboxAddress, long successfulRestoreCount,
                                     long errorRestoreCount, Instant timestamp) {
            this.teamMailboxAddress = teamMailboxAddress;
            this.successfulRestoreCount = successfulRestoreCount;
            this.errorRestoreCount = errorRestoreCount;
            this.timestamp = timestamp;
        }

        public String getTeamMailboxAddress() {
            return teamMailboxAddress;
        }

        public long getSuccessfulRestoreCount() {
            return successfulRestoreCount;
        }

        public long getErrorRestoreCount() {
            return errorRestoreCount;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TeamMailboxVaultRestoreTask.class);

    private final TeamMailboxRestoreService restoreService;
    private final TeamMailbox teamMailbox;
    private final AtomicLong successfulRestoreCount;
    private final AtomicLong errorRestoreCount;

    public TeamMailboxVaultRestoreTask(TeamMailboxRestoreService restoreService, TeamMailbox teamMailbox) {
        this.restoreService = restoreService;
        this.teamMailbox = teamMailbox;
        this.successfulRestoreCount = new AtomicLong();
        this.errorRestoreCount = new AtomicLong();
    }

    @Override
    public Result run() {
        try {
            return restoreService.restore(teamMailbox.self(), Query.ALL).toStream()
                .peek(this::updateInformation)
                .map(this::toTaskResult)
                .reduce(Task::combine)
                .orElse(Result.COMPLETED);
        } catch (MailboxException e) {
            LOGGER.error("Error restoring deleted messages for team mailbox {}", teamMailbox.asMailAddress().asString(), e);
            return Result.PARTIAL;
        }
    }

    private Result toTaskResult(TeamMailboxRestoreService.RestoreResult restoreResult) {
        return restoreResult == TeamMailboxRestoreService.RestoreResult.RESTORE_SUCCEED ? Result.COMPLETED : Result.PARTIAL;
    }

    private void updateInformation(TeamMailboxRestoreService.RestoreResult restoreResult) {
        switch (restoreResult) {
            case RESTORE_SUCCEED -> successfulRestoreCount.incrementAndGet();
            case RESTORE_FAILED -> errorRestoreCount.incrementAndGet();
        }
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(
            teamMailbox.asMailAddress().asString(),
            successfulRestoreCount.get(),
            errorRestoreCount.get(),
            Clock.systemUTC().instant()));
    }

    public TeamMailbox getTeamMailbox() {
        return teamMailbox;
    }
}
