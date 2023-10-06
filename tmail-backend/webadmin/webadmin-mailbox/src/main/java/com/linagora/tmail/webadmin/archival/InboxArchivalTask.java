package com.linagora.tmail.webadmin.archival;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.Username;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

public class InboxArchivalTask implements Task {
    public static final TaskType TASK_TYPE = TaskType.of("InboxArchivalTask");
    private static final int MAX_STORED_FAILED_USERS = 100;

    public record AdditionalInformation(Instant timestamp, long archivedMessageCount, long errorMessageCount,
                                        long successfulUsersCount, long failedUsersCount,
                                        Set<Username> failedUsers) implements TaskExecutionDetails.AdditionalInformation {

        private static AdditionalInformation from(Context context) {
            Context.Snapshot snapshot = context.snapshot();
            return new AdditionalInformation(Clock.systemUTC().instant(), snapshot.archivedMessageCount(), snapshot.errorMessageCount(),
                snapshot.successfulUsersCount, snapshot.failedUsersCount, snapshot.failedUsers);
        }
    }

    public static class Context {

        public record Snapshot(long archivedMessageCount, long errorMessageCount, long successfulUsersCount,
                               long failedUsersCount, Set<Username> failedUsers) {

            public static Builder builder() {
                return new Builder();
            }

            static class Builder {
                private Optional<Long> archivedMessageCount;
                private Optional<Long> errorMessageCount;
                private Optional<Long> successfulUsersCount;
                private Optional<Long> failedUsersCount;
                private Optional<Set<Username>> failedUsers;

                Builder() {
                    archivedMessageCount = Optional.empty();
                    errorMessageCount = Optional.empty();
                    successfulUsersCount = Optional.empty();
                    failedUsersCount = Optional.empty();
                    failedUsers = Optional.empty();
                }

                public Snapshot build() {
                    return new Snapshot(archivedMessageCount.orElse(0L),
                        errorMessageCount.orElse(0L),
                        successfulUsersCount.orElse(0L),
                        failedUsersCount.orElse(0L),
                        failedUsers.orElse(ImmutableSet.of()));
                }

                public Builder archivedMessageCount(long archivedMessageCount) {
                    this.archivedMessageCount = Optional.of(archivedMessageCount);
                    return this;
                }

                public Builder errorMessageCount(long errorMessageCount) {
                    this.errorMessageCount = Optional.of(errorMessageCount);
                    return this;
                }

                public Builder successfulUsersCount(long successfulUsersCount) {
                    this.successfulUsersCount = Optional.of(successfulUsersCount);
                    return this;
                }

                public Builder failedUsersCount(long failedUsersCount) {
                    this.failedUsersCount = Optional.of(failedUsersCount);
                    return this;
                }

                public Builder failedUsers(Set<Username> failedUsers) {
                    this.failedUsers = Optional.of(failedUsers);
                    return this;
                }
            }
        }

        private final AtomicLong archivedMessageCount;
        private final AtomicLong errorMessageCount;
        private final AtomicLong successfulUsersCount;
        private final AtomicLong failedUsersCount;
        private final Set<Username> failedUsers;

        public Context() {
            this.archivedMessageCount = new AtomicLong();
            this.errorMessageCount = new AtomicLong();
            this.successfulUsersCount = new AtomicLong();
            this.failedUsersCount = new AtomicLong();
            this.failedUsers = ConcurrentHashMap.newKeySet();
        }

        public void increaseArchivedMessageCount(int count) {
            archivedMessageCount.addAndGet(count);
        }

        public void increaseErrorMessageCount() {
            errorMessageCount.incrementAndGet();
        }

        public void increaseSuccessfulUsers() {
            successfulUsersCount.incrementAndGet();
        }

        public void increaseFailedUsers() {
            failedUsersCount.incrementAndGet();
        }

        public void addFailedUser(Username username) {
            if (failedUsers.size() < MAX_STORED_FAILED_USERS) {
                failedUsers.add(username);
            }
        }

        public long getSuccessfulUsersCount() {
            return successfulUsersCount.get();
        }

        public long getFailedUsersCount() {
            return failedUsersCount.get();
        }

        public Set<Username> getFailedUsers() {
            return failedUsers;
        }

        public Snapshot snapshot() {
            return Snapshot.builder()
                .archivedMessageCount(archivedMessageCount.get())
                .errorMessageCount(errorMessageCount.get())
                .successfulUsersCount(successfulUsersCount.get())
                .failedUsersCount(failedUsersCount.get())
                .failedUsers(failedUsers)
                .build();
        }
    }

    private final InboxArchivalService inboxArchivalService;
    private final Context context;

    public InboxArchivalTask(InboxArchivalService inboxArchivalService) {
        this.inboxArchivalService = inboxArchivalService;
        this.context = new Context();
    }

    @Override
    public Result run() {
        return inboxArchivalService.archiveInbox(context)
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(context));
    }

    @VisibleForTesting
    public Context.Snapshot snapshot() {
        return context.snapshot();
    }
}
