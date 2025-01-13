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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.apache.james.task.TaskType;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class TasksHealthCheckConfigurationTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(TasksHealthCheckConfiguration.class)
            .verify();
    }

    @Test
    void emptyStringCaseShouldReturnEmptyMap() {
        assertThat(TasksHealthCheckConfiguration.from("").taskTypeDurationMap())
            .isEmpty();
    }

    @Test
    void normalCaseShouldReturnExactMap() {
        assertThat(TasksHealthCheckConfiguration.from("TaskAName:2day,TaskBName:2month,TaskCName:2week").taskTypeDurationMap())
            .containsExactlyInAnyOrderEntriesOf(Map.of(
                TaskType.of("TaskAName"), ChronoUnit.DAYS.getDuration().multipliedBy(2),
                TaskType.of("TaskBName"), ChronoUnit.MONTHS.getDuration().multipliedBy(2),
                TaskType.of("TaskCName"), ChronoUnit.WEEKS.getDuration().multipliedBy(2)));
    }

    @Test
    void useAEmptyStringEntryShouldIgnoreIt() {
        assertThat(TasksHealthCheckConfiguration.from("TaskAName:2day,,TaskCName:2week").taskTypeDurationMap())
            .containsExactlyInAnyOrderEntriesOf(Map.of(
                TaskType.of("TaskAName"), ChronoUnit.DAYS.getDuration().multipliedBy(2),
                TaskType.of("TaskCName"), ChronoUnit.WEEKS.getDuration().multipliedBy(2)));
    }

    @Test
    void useAWrongDelimiterCharacterCaseShouldThrowException() {
        assertThatThrownBy(() -> TasksHealthCheckConfiguration.from("TaskAName:2day;TaskBName:2month;TaskCName:2week"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Chunk [TaskAName:2day;TaskBName:2month;TaskCName:2week] is not a valid entry");
    }

    @Test
    void useWrongDurationFormatShouldThrowException() {
        assertThatThrownBy(() -> TasksHealthCheckConfiguration.from("TaskAName:2dayyyy"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void useNegativeDurationShouldThrowException() {
        assertThatThrownBy(() -> TasksHealthCheckConfiguration.from("TaskAName:-2days"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Duration amount should be positive");
    }

}
