package com.linagora.tmail.webadmin.archival;

import javax.inject.Inject;

import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;

public class InboxArchivalTaskRegistration extends TaskFromRequestRegistry.TaskRegistration {
    private static final TaskRegistrationKey INBOX_ARCHIVAL = TaskRegistrationKey.of("InboxArchival");

    @Inject
    public InboxArchivalTaskRegistration(InboxArchivalService inboxArchivalService) {
        super(INBOX_ARCHIVAL, request -> new InboxArchivalTask(inboxArchivalService));
    }
}
