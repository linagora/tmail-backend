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

package com.linagora.tmail.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.task.Hostname;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskType;
import org.apache.james.task.eventsourcing.MemoryTaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class TasksHeathCheckTest {
    private static final ZonedDateTime NOW = ZonedDateTime.of(LocalDateTime.of(2022, 10, 10, 0, 0), ZoneId.of("Europe/Paris"));
    private static final TaskType TASK_TYPE_A = TaskType.of("TaskA");
    private static final TaskType TASK_TYPE_B = TaskType.of("TaskB");
    private static final TaskType TASK_TYPE_C = TaskType.of("TaskC");
    private static final TaskType TASK_TYPE_D = TaskType.of("TaskD");
    private static final TaskType TASK_TYPE_E = TaskType.of("TaskE");
    private static final ZonedDateTime SUBMITTED_DATE = ZonedDateTime.of(LocalDateTime.of(2021, 1, 1, 0, 0), ZoneId.of("Europe/Paris"));
    private static final Hostname SUBMITTED_NODE = new Hostname("foo");
    private static final TaskExecutionDetails TASK_A_COMPLETED_IN_TIME = new TaskExecutionDetails(TaskId.generateTaskId(), TASK_TYPE_A, TaskManager.Status.COMPLETED, SUBMITTED_DATE, SUBMITTED_NODE,
        Optional::empty, Optional.empty(), Optional.empty(), Optional.of(NOW.minusDays(1)), Optional.empty(), Optional.empty(), Optional.empty());
    private static final TaskExecutionDetails TASK_A_COMPLETED_NOT_IN_TIME_1 = new TaskExecutionDetails(TaskId.generateTaskId(), TASK_TYPE_A, TaskManager.Status.COMPLETED, SUBMITTED_DATE, SUBMITTED_NODE,
        Optional::empty, Optional.empty(), Optional.empty(), Optional.of(NOW.minusDays(10)), Optional.empty(), Optional.empty(), Optional.empty());
    private static final TaskExecutionDetails TASK_A_COMPLETED_NOT_IN_TIME_2 = new TaskExecutionDetails(TaskId.generateTaskId(), TASK_TYPE_A, TaskManager.Status.COMPLETED, SUBMITTED_DATE, SUBMITTED_NODE,
        Optional::empty, Optional.empty(), Optional.empty(), Optional.of(NOW.minusDays(15)), Optional.empty(), Optional.empty(), Optional.empty());
    private static final TaskExecutionDetails TASK_B_COMPLETED_IN_TIME = new TaskExecutionDetails(TaskId.generateTaskId(), TASK_TYPE_B, TaskManager.Status.COMPLETED, SUBMITTED_DATE, SUBMITTED_NODE,
        Optional::empty, Optional.empty(), Optional.empty(), Optional.of(NOW.minusDays(1)), Optional.empty(), Optional.empty(), Optional.empty());
    private static final TaskExecutionDetails TASK_B_COMPLETED_NOT_IN_TIME_1 = new TaskExecutionDetails(TaskId.generateTaskId(), TASK_TYPE_B, TaskManager.Status.COMPLETED, SUBMITTED_DATE, SUBMITTED_NODE,
        Optional::empty, Optional.empty(), Optional.empty(), Optional.of(NOW.minusDays(10)), Optional.empty(), Optional.empty(), Optional.empty());
    private static final TaskExecutionDetails TASK_B_COMPLETED_NOT_IN_TIME_2 = new TaskExecutionDetails(TaskId.generateTaskId(), TASK_TYPE_B, TaskManager.Status.COMPLETED, SUBMITTED_DATE, SUBMITTED_NODE,
        Optional::empty, Optional.empty(), Optional.empty(), Optional.of(NOW.minusDays(15)), Optional.empty(), Optional.empty(), Optional.empty());
    private static final TaskExecutionDetails TASK_C_IN_PROGRESS = new TaskExecutionDetails(TaskId.generateTaskId(), TASK_TYPE_C, TaskManager.Status.IN_PROGRESS, SUBMITTED_DATE, SUBMITTED_NODE,
        Optional::empty, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    private static final TaskExecutionDetails TASK_D_CANCELED = new TaskExecutionDetails(TaskId.generateTaskId(), TASK_TYPE_D, TaskManager.Status.CANCELLED, SUBMITTED_DATE, SUBMITTED_NODE,
        Optional::empty, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    private static final TaskExecutionDetails TASK_E_FAILED = new TaskExecutionDetails(TaskId.generateTaskId(), TASK_TYPE_E, TaskManager.Status.FAILED, SUBMITTED_DATE, SUBMITTED_NODE,
        Optional::empty, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    private TaskExecutionDetailsProjection tasksProjection;
    private TasksHeathCheck testee;

    @BeforeEach
    void setup() {
        tasksProjection = new MemoryTaskExecutionDetailsProjection();
    }

    @Test
    void shouldReturnComponentName() {
        testee = new TasksHeathCheck(tasksProjection, TasksHealthCheckConfiguration.DEFAULT_CONFIGURATION, new UpdatableTickingClock(NOW.toInstant()));

        assertThat(testee.componentName().getName()).isEqualTo("Tasks execution");
    }

    @Test
    void shouldReturnHealthyWhenDefaultConfiguration() {
        testee = new TasksHeathCheck(tasksProjection, TasksHealthCheckConfiguration.DEFAULT_CONFIGURATION, new UpdatableTickingClock(NOW.toInstant()));

        assertThat(Mono.from(testee.check()).block().getStatus()).isEqualTo(ResultStatus.HEALTHY);
    }

    @Test
    void shouldReturnUnhealthyWhenNoCompletedTasks() {
        testee = new TasksHeathCheck(tasksProjection, TasksHealthCheckConfiguration.from("TaskA:2day,TaskB:5day"), new UpdatableTickingClock(NOW.toInstant()));

        assertThat(Mono.from(testee.check()).block().getStatus()).isEqualTo(ResultStatus.UNHEALTHY);
    }

    @Test
    void shouldReturnHealthyWhenATaskCompleted() {
        testee = new TasksHeathCheck(tasksProjection, TasksHealthCheckConfiguration.from("TaskA:2day"), new UpdatableTickingClock(NOW.toInstant()));
        tasksProjection.update(TASK_A_COMPLETED_IN_TIME);

        assertThat(Mono.from(testee.check()).block().getStatus()).isEqualTo(ResultStatus.HEALTHY);
    }

    @Test
    void onlyInProgressTaskShouldReturnUnhealthy() {
        testee = new TasksHeathCheck(tasksProjection, TasksHealthCheckConfiguration.from("TaskC:2day"), new UpdatableTickingClock(NOW.toInstant()));
        tasksProjection.update(TASK_C_IN_PROGRESS);

        assertThat(Mono.from(testee.check()).block().getStatus()).isEqualTo(ResultStatus.UNHEALTHY);
    }

    @Test
    void onlyCanceledAndFailedTasksShouldReturnUnhealthy() {
        testee = new TasksHeathCheck(tasksProjection, TasksHealthCheckConfiguration.from("TaskD:2day,TaskE:2day"), new UpdatableTickingClock(NOW.toInstant()));
        tasksProjection.update(TASK_D_CANCELED);
        tasksProjection.update(TASK_E_FAILED);

        assertThat(Mono.from(testee.check()).block().getStatus()).isEqualTo(ResultStatus.UNHEALTHY);
    }

    @Test
    void mixCaseWithOnlyOneCompletedTaskShouldReturnDegraded() {
        testee = new TasksHeathCheck(tasksProjection, TasksHealthCheckConfiguration.from("TaskA:2day,TaskB:5day,TaskC:2day,TaskD:2day,TaskE:2day"), new UpdatableTickingClock(NOW.toInstant()));
        tasksProjection.update(TASK_A_COMPLETED_IN_TIME);
        tasksProjection.update(TASK_B_COMPLETED_NOT_IN_TIME_1);
        tasksProjection.update(TASK_C_IN_PROGRESS);
        tasksProjection.update(TASK_D_CANCELED);
        tasksProjection.update(TASK_E_FAILED);

        assertThat(Mono.from(testee.check()).block().getStatus()).isEqualTo(ResultStatus.DEGRADED);
    }

    @RepeatedTest(10)
    void shouldTakeTheLatestFinishedTaskForEachTaskTypeAndReturnHealthyWhenAllTasksFinishedWithinTime() {
        testee = new TasksHeathCheck(tasksProjection, TasksHealthCheckConfiguration.from("TaskA:2day,TaskB:2day"), new UpdatableTickingClock(NOW.toInstant()));
        tasksProjection.update(TASK_A_COMPLETED_NOT_IN_TIME_1);
        tasksProjection.update(TASK_A_COMPLETED_NOT_IN_TIME_2);
        tasksProjection.update(TASK_A_COMPLETED_IN_TIME);
        tasksProjection.update(TASK_B_COMPLETED_IN_TIME);
        tasksProjection.update(TASK_B_COMPLETED_NOT_IN_TIME_1);

        assertThat(Mono.from(testee.check()).block().getStatus()).isEqualTo(ResultStatus.HEALTHY);
    }

    @RepeatedTest(10)
    void shouldTakeTheLatestFinishedTaskForEachTaskTypeAndReturnDegradedWhenSomeTasksFinishedWithinTime() {
        testee = new TasksHeathCheck(tasksProjection, TasksHealthCheckConfiguration.from("TaskA:2day,TaskB:2day"), new UpdatableTickingClock(NOW.toInstant()));
        tasksProjection.update(TASK_A_COMPLETED_NOT_IN_TIME_1);
        tasksProjection.update(TASK_A_COMPLETED_NOT_IN_TIME_2);
        tasksProjection.update(TASK_A_COMPLETED_IN_TIME);
        tasksProjection.update(TASK_B_COMPLETED_NOT_IN_TIME_1);
        tasksProjection.update(TASK_B_COMPLETED_NOT_IN_TIME_2);

        assertThat(Mono.from(testee.check()).block().getStatus()).isEqualTo(ResultStatus.DEGRADED);
    }

    @RepeatedTest(10)
    void shouldTakeTheLatestFinishedTaskForEachTaskTypeAndReturnUnhealthyWhenNoTasksFinishedWithinTime() {
        testee = new TasksHeathCheck(tasksProjection, TasksHealthCheckConfiguration.from("TaskA:2day,TaskB:2day"), new UpdatableTickingClock(NOW.toInstant()));
        tasksProjection.update(TASK_A_COMPLETED_NOT_IN_TIME_1);
        tasksProjection.update(TASK_A_COMPLETED_NOT_IN_TIME_2);
        tasksProjection.update(TASK_B_COMPLETED_NOT_IN_TIME_1);
        tasksProjection.update(TASK_B_COMPLETED_NOT_IN_TIME_2);

        assertThat(Mono.from(testee.check()).block().getStatus()).isEqualTo(ResultStatus.UNHEALTHY);
    }
}
