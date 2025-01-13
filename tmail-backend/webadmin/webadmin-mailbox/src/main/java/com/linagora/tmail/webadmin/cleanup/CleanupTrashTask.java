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

import java.time.Clock;
import java.util.Optional;

import org.apache.james.mailbox.Role;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class CleanupTrashTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("cleanup-trash");

    private final CleanupService cleanupService;
    private final RunningOptions runningOptions;
    private final CleanupContext context;

    public CleanupTrashTask(CleanupService cleanupService, RunningOptions runningOptions) {
        this.cleanupService = cleanupService;
        this.runningOptions = runningOptions;
        this.context = new CleanupContext();
    }

    @Override
    public Result run() throws InterruptedException {
        return cleanupService.cleanup(Role.TRASH, runningOptions, context).block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        CleanupContext.Snapshot snapshot = context.snapshot();
        return Optional.of(new CleanupTrashTaskDetails(Clock.systemUTC().instant(),
            snapshot.processedUsersCount(),
            snapshot.deletedMessagesCount(),
            snapshot.failedUsers(),
            runningOptions));
    }
}
