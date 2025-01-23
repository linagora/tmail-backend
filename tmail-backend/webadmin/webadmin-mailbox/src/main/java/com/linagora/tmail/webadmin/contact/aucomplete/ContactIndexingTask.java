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

package com.linagora.tmail.webadmin.contact.aucomplete;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class ContactIndexingTask implements Task {
    public static final TaskType TASK_TYPE = TaskType.of("ContactIndexing");

    public record RunningOptions(int usersPerSecond) {
        public static final int DEFAULT_USERS_PER_SECOND = 1;
        public static final RunningOptions DEFAULT = new RunningOptions(DEFAULT_USERS_PER_SECOND);

        public RunningOptions {
            Preconditions.checkArgument(usersPerSecond > 0, "'usersPerSecond' needs to be strictly positive");
        }

        public static RunningOptions of(int usersPerSecond) {
            return new RunningOptions(usersPerSecond);
        }
    }

    public record Details(Instant instant,
                          long processedUsersCount,
                          long indexedContactsCount,
                          long failedContactsCount,
                          ImmutableList<String> failedUsers,
                          RunningOptions runningOptions) implements TaskExecutionDetails.AdditionalInformation {
        @Override
        public Instant timestamp() {
            return instant;
        }
    }

    private final ContactIndexingService contactIndexingService;
    private final ContactIndexingContext context;
    private final RunningOptions runningOptions;

    public ContactIndexingTask(ContactIndexingService contactIndexingService,
                               RunningOptions runningOptions) {
        this.contactIndexingService = contactIndexingService;
        this.runningOptions = runningOptions;
        this.context = new ContactIndexingContext();
    }

    @Override
    public Task.Result run() {
        return contactIndexingService.indexAllContacts(runningOptions, context).block();
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
        ContactIndexingContext.Snapshot snapshot = context.snapshot();
        return Optional.of(new Details(Clock.systemUTC().instant(),
            snapshot.processedUsersCount(),
            snapshot.indexedContactsCount(),
            snapshot.failedContactsCount(),
            snapshot.failedUsers(),
            runningOptions));
    }
}
