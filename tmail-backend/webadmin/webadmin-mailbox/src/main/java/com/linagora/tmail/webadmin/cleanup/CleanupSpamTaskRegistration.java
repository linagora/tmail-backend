package com.linagora.tmail.webadmin.cleanup;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;

import spark.Request;

public class CleanupSpamTaskRegistration extends TaskFromRequestRegistry.TaskRegistration {
    private static final String USERS_PER_SECOND_PARA = "usersPerSecond";
    private static final TaskRegistrationKey CLEANUP_SPAM = TaskRegistrationKey.of("CleanupSpam");

    @Inject
    public CleanupSpamTaskRegistration(CleanupService cleanupService) {
        super(CLEANUP_SPAM, request -> new CleanupSpamTask(cleanupService, parseRunningOptions(request)));
    }

    private static RunningOptions parseRunningOptions(Request request) {
        return RunningOptions.of(intQueryParameter(request).orElse(RunningOptions.DEFAULT_USERS_PER_SECOND));
    }

    private static Optional<Integer> intQueryParameter(Request request) {
        try {
            return Optional.ofNullable(request.queryParams(USERS_PER_SECOND_PARA))
                .map(Integer::parseInt);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Illegal value supplied for query parameter '%s', expecting a " +
                "strictly positive optional integer", USERS_PER_SECOND_PARA), e);
        }
    }
}
