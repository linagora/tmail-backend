package com.linagora.tmail.webadmin.cleanup;

import java.util.Optional;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class CleanupTrashTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("cleanup-trash");

    private final CleanupTrashService cleanupTrashService;
    private final RunningOptions runningOptions;

    public CleanupTrashTask(CleanupTrashService cleanupTrashService, RunningOptions runningOptions) {
        this.cleanupTrashService = cleanupTrashService;
        this.runningOptions = runningOptions;
    }

    @Override
    public Result run() throws InterruptedException {
        return cleanupTrashService.cleanupTrash(runningOptions).block();
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
        return Task.super.details();
    }
}
