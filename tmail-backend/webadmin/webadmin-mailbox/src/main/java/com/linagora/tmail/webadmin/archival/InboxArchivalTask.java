package com.linagora.tmail.webadmin.archival;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

public class InboxArchivalTask implements Task {
    public static final TaskType TASK_TYPE = TaskType.of("InboxArchivalTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private static AdditionalInformation from(Context context) {
            Context.Snapshot snapshot = context.snapshot();
            return new AdditionalInformation(Clock.systemUTC().instant(), snapshot.getArchivedMessageCount(), snapshot.getErrorMessageCount());
        }

        private final Instant timestamp;
        private final long archivedMessageCount;
        private final long errorMessageCount;

        public AdditionalInformation(Instant timestamp, long archivedMessageCount, long errorMessageCount) {
            this.timestamp = timestamp;
            this.archivedMessageCount = archivedMessageCount;
            this.errorMessageCount = errorMessageCount;
        }

        public long getArchivedMessageCount() {
            return archivedMessageCount;
        }

        public long getErrorMessageCount() {
            return errorMessageCount;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof AdditionalInformation that) {
                return Objects.equals(this.archivedMessageCount, that.archivedMessageCount)
                    && Objects.equals(this.errorMessageCount, that.errorMessageCount)
                    && Objects.equals(this.timestamp, that.timestamp);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(timestamp, archivedMessageCount, errorMessageCount);
        }
    }

    public static class Context {

        public static class Snapshot {

            public static Builder builder() {
                return new Builder();
            }

            static class Builder {
                private Optional<Long> archivedMessageCount;
                private Optional<Long> errorMessageCount;

                Builder() {
                    archivedMessageCount = Optional.empty();
                    errorMessageCount = Optional.empty();
                }

                public Snapshot build() {
                    return new Snapshot(archivedMessageCount.orElse(0L),
                        errorMessageCount.orElse(0L));
                }

                public Builder archivedMessageCount(long archivedMessageCount) {
                    this.archivedMessageCount = Optional.of(archivedMessageCount);
                    return this;
                }

                public Builder errorMessageCount(long errorMessageCount) {
                    this.errorMessageCount = Optional.of(errorMessageCount);
                    return this;
                }
            }

            private final long archivedMessageCount;
            private final long errorMessageCount;

            public Snapshot(long archivedMessageCount, long errorMessageCount) {
                this.archivedMessageCount = archivedMessageCount;
                this.errorMessageCount = errorMessageCount;
            }

            public long getArchivedMessageCount() {
                return archivedMessageCount;
            }

            public long getErrorMessageCount() {
                return errorMessageCount;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot snapshot) {
                    return Objects.equals(this.archivedMessageCount, snapshot.archivedMessageCount)
                        && Objects.equals(this.errorMessageCount, snapshot.errorMessageCount);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(archivedMessageCount, errorMessageCount);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("archivedMessageCount", archivedMessageCount)
                    .add("errorMessageCount", errorMessageCount)
                    .toString();
            }
        }

        private final AtomicLong archivedMessageCount;
        private final AtomicLong errorMessageCount;

        public Context() {
            this.archivedMessageCount = new AtomicLong();
            this.errorMessageCount = new AtomicLong();
        }

        public void increaseArchivedMessageCount(int count) {
            archivedMessageCount.addAndGet(count);
        }

        public void increaseErrorMessageCount() {
            errorMessageCount.incrementAndGet();
        }

        public Snapshot snapshot() {
            return Snapshot.builder()
                .archivedMessageCount(archivedMessageCount.get())
                .errorMessageCount(errorMessageCount.get())
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
