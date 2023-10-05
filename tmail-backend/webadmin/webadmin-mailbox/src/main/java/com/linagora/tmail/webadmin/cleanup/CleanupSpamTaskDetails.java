package com.linagora.tmail.webadmin.cleanup;

import java.time.Instant;

import org.apache.james.task.TaskExecutionDetails;

import com.google.common.collect.ImmutableList;

public record CleanupSpamTaskDetails(Instant instant,
                                     long processedUsersCount,
                                     long deletedMessagesCount,
                                     ImmutableList<String> failedUsers,
                                     RunningOptions runningOptions) implements TaskExecutionDetails.AdditionalInformation {
    @Override
    public Instant timestamp() {
        return instant;
    }
}
