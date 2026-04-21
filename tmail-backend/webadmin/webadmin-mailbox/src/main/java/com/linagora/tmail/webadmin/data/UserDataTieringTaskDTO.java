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

package com.linagora.tmail.webadmin.data;

import java.time.Duration;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.tiering.UserDataTieringService;

public record UserDataTieringTaskDTO(
    @JsonProperty("type") String type,
    @JsonProperty("username") String username,
    @JsonProperty("tieringSeconds") long tieringSeconds,
    @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions
) implements TaskDTO {

    public record RunningOptionsDTO(@JsonProperty("messagesPerSecond") int messagesPerSecond) {
        public static RunningOptionsDTO of(RunningOptions options) {
            return new RunningOptionsDTO(options.messagesPerSecond());
        }

        public RunningOptions asDomainObject() {
            return new RunningOptions(messagesPerSecond);
        }
    }

    public static TaskDTOModule<UserDataTieringTask, UserDataTieringTaskDTO> module(UserDataTieringService service) {
        return DTOModule.forDomainObject(UserDataTieringTask.class)
            .convertToDTO(UserDataTieringTaskDTO.class)
            .toDomainObjectConverter(dto -> new UserDataTieringTask(
                service,
                Username.of(dto.username()),
                Duration.ofSeconds(dto.tieringSeconds()),
                dto.runningOptions().map(RunningOptionsDTO::asDomainObject).orElse(RunningOptions.DEFAULT)))
            .toDTOConverter((task, type) -> new UserDataTieringTaskDTO(
                type,
                task.getUsername().asString(),
                task.getTiering().getSeconds(),
                Optional.of(RunningOptionsDTO.of(task.getRunningOptions()))))
            .typeName(UserDataTieringTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    public String getType() {
        return type;
    }
}
