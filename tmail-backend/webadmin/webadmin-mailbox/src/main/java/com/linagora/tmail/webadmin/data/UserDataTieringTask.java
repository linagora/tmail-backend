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

package com.linagora.tmail.webadmin.data;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.linagora.tmail.tiering.UserDataTieringContext;
import com.linagora.tmail.tiering.UserDataTieringService;

import reactor.core.publisher.Mono;

public class UserDataTieringTask implements Task {

    public static final TaskType TASK_TYPE = TaskType.of("UserDataTieringTask");

    public record AdditionalInformation(
        Instant timestamp,
        String username,
        long tieringSeconds,
        RunningOptions runningOptions,
        long tieredMessageCount,
        long failedMessageCount
    ) implements TaskExecutionDetails.AdditionalInformation {

        static AdditionalInformation from(UserDataTieringTask task) {
            UserDataTieringContext.Snapshot snapshot = task.context.snapshot();
            return new AdditionalInformation(
                Clock.systemUTC().instant(),
                task.username.asString(),
                task.tiering.getSeconds(),
                task.runningOptions,
                snapshot.tieredMessageCount(),
                snapshot.failedMessageCount());
        }
    }

    private final UserDataTieringService tieringService;
    private final Username username;
    private final Duration tiering;
    private final RunningOptions runningOptions;
    private final UserDataTieringContext context;

    public UserDataTieringTask(UserDataTieringService tieringService, Username username, Duration tiering, RunningOptions runningOptions) {
        this.tieringService = tieringService;
        this.username = username;
        this.tiering = tiering;
        this.runningOptions = runningOptions;
        this.context = new UserDataTieringContext();
    }

    @Override
    public Result run() {
        return tieringService.tierUserData(username, tiering, context, runningOptions.messagesPerSecond())
            .then(Mono.fromSupplier(() -> context.snapshot().failedMessageCount() > 0 ? Result.PARTIAL : Result.COMPLETED))
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(this));
    }

    public Username getUsername() {
        return username;
    }

    public Duration getTiering() {
        return tiering;
    }

    public RunningOptions getRunningOptions() {
        return runningOptions;
    }

    @VisibleForTesting
    public UserDataTieringContext.Snapshot snapshot() {
        return context.snapshot();
    }
}
