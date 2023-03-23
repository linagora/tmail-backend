package com.linagora.tmail.healthcheck;

import static org.apache.james.task.TaskManager.Status.COMPLETED;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;

public class TasksHeathCheck implements HealthCheck {
    public static final ComponentName COMPONENT_NAME = new ComponentName("Tasks execution");
    private static final int AVERAGE_CONCURRENCY_LEVEL = 4;

    private final TaskExecutionDetailsProjection taskExecutionDetailsProjection;
    private final TasksHealthCheckConfiguration configuration;
    private final Clock clock;

    @Inject
    public TasksHeathCheck(TaskExecutionDetailsProjection taskExecutionDetailsProjection,
                           TasksHealthCheckConfiguration configuration,
                           Clock clock) {
        this.taskExecutionDetailsProjection = taskExecutionDetailsProjection;
        this.configuration = configuration;
        this.clock = clock;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Publisher<Result> check() {
        ZonedDateTime now = ZonedDateTime.now(clock);

        if (configuration.taskTypeDurationMap().isEmpty()) {
            return Mono.just(Result.healthy(COMPONENT_NAME));
        } else {
            return Flux.from(taskExecutionDetailsProjection.listReactive())
                .filter(finishedTask())
                .groupBy(TaskExecutionDetails::getType)
                .flatMap(getTheLatestFinishedTask(), AVERAGE_CONCURRENCY_LEVEL)
                .filter(complyAnyOfTasksHealthCheck(now))
                .count()
                .map(evaluateHeathCheckResult())
                .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Could not check tasks execution information", e)))
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
        }
    }

    private Predicate<TaskExecutionDetails> finishedTask() {
        return taskExecutionDetails -> taskExecutionDetails.getStatus().equals(COMPLETED);
    }

    private Function<GroupedFlux<TaskType, TaskExecutionDetails>, Publisher<? extends TaskExecutionDetails>> getTheLatestFinishedTask() {
        return groupedTask -> groupedTask
            .sort(Comparator.comparing(finishedTask -> finishedTask.getCompletedDate().get()))
            .takeLast(1);
    }

    private Predicate<TaskExecutionDetails> complyAnyOfTasksHealthCheck(ZonedDateTime now) {
        return finishedTask -> configuration.taskTypeDurationMap().entrySet()
            .stream()
            .anyMatch(entry -> matchTheTaskTypeAndInTheRequiredExecutionDuration(entry, finishedTask, now));
    }

    private boolean matchTheTaskTypeAndInTheRequiredExecutionDuration(Map.Entry<TaskType, Duration> entry, TaskExecutionDetails finishedTask, ZonedDateTime now) {
        return entry.getKey().equals(finishedTask.getType()) && Duration.between(finishedTask.getCompletedDate().get(), now).compareTo(entry.getValue()) <= 0;
    }

    private Function<Long, Result> evaluateHeathCheckResult() {
        return count -> {
            if (allTasksFinishedWithinTime(count)) {
                return Result.healthy(COMPONENT_NAME);
            } else if (noTaskFinishedWithinTime(count)) {
                return Result.unhealthy(COMPONENT_NAME, "There is no task finished within the time required");
            } else {
                return Result.degraded(COMPONENT_NAME, "There are some tasks not finished within the time required");
            }
        };
    }

    private boolean allTasksFinishedWithinTime(Long count) {
        return count == configuration.taskTypeDurationMap().size();
    }

    private boolean noTaskFinishedWithinTime(Long count) {
        return count == 0;
    }

}
