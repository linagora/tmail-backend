package com.linagora.tmail.webadmin.archival;

import java.util.Optional;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class InboxArchivalTask implements Task {
    private static final TaskType TASK_TYPE = TaskType.of("InboxArchivalTask");

    private final InboxArchivalService inboxArchivalService;

    public InboxArchivalTask(InboxArchivalService inboxArchivalService) {
        this.inboxArchivalService = inboxArchivalService;
    }

    @Override
    public Result run() {
        return inboxArchivalService.archiveInbox()
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        // TODO some useful information
        return Task.super.details();
    }
}
