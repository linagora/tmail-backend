package com.linagora.tmail.webadmin.cleanup;

import java.time.Clock;
import java.util.Optional;

import org.apache.james.mailbox.Role;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class CleanupSpamTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("cleanup-spam");

    private final CleanupService cleanupService;
    private final RunningOptions runningOptions;
    private final CleanupContext context;

    public CleanupSpamTask(CleanupService cleanupService, RunningOptions runningOptions) {
        this.cleanupService = cleanupService;
        this.runningOptions = runningOptions;
        this.context =  new CleanupContext();
    }

    @Override
    public Task.Result run() throws InterruptedException {
        return cleanupService.cleanup(Role.SPAM, runningOptions, context).block();
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
        return Optional.of(new CleanupSpamTaskDetails(Clock.systemUTC().instant(),
            snapshot.processedUsersCount(),
            snapshot.deletedMessagesCount(),
            snapshot.failedUsers(),
            runningOptions));
    }
}
