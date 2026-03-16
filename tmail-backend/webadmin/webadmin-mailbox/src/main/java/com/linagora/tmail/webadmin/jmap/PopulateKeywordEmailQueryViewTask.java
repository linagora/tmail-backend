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

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class PopulateKeywordEmailQueryViewTask implements Task {
    public record AdditionalInformation(Instant instant,
                                        long processedUserCount,
                                        long processedMessageCount,
                                        long provisionedKeywordViewCount,
                                        long failedUserCount,
                                        long failedMessageCount,
                                        RunningOptions runningOptions) implements TaskExecutionDetails.AdditionalInformation {
        @Override
        public Instant timestamp() {
            return instant;
        }
    }

    public static final TaskType TASK_TYPE = TaskType.of("PopulateKeywordEmailQueryViewTask");

    private final KeywordEmailQueryViewPopulator populator;
    private final KeywordEmailQueryViewPopulator.Progress progress;
    private final RunningOptions runningOptions;

    public PopulateKeywordEmailQueryViewTask(KeywordEmailQueryViewPopulator populator, RunningOptions runningOptions) {
        this.populator = populator;
        this.runningOptions = runningOptions;
        this.progress = new KeywordEmailQueryViewPopulator.Progress();
    }

    @Override
    public Result run() {
        return populator.populateView(progress, runningOptions)
            .block();
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
        return Optional.of(new AdditionalInformation(
            Clock.systemUTC().instant(),
            progress.getProcessedUserCount(),
            progress.getProcessedMessageCount(),
            progress.getProvisionedKeywordViewCount(),
            progress.getFailedUserCount(),
            progress.getFailedMessageCount(),
            runningOptions));
    }
}
