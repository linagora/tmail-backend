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

package com.linagora.tmail.space;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SpacePurgeTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("twake-space-purge");

    public record Details(Instant timestamp, long deletedTeamMailboxes, List<String> failedTeamMailboxes)
        implements TaskExecutionDetails.AdditionalInformation {
    }

    public record SpacePurgeTaskDTO(@JsonProperty("type") String type) implements TaskDTO {
        public static TaskDTOModule<SpacePurgeTask, SpacePurgeTaskDTO> module(SpacePurge purge) {
            return DTOModule.forDomainObject(SpacePurgeTask.class)
                .convertToDTO(SpacePurgeTaskDTO.class)
                .toDomainObjectConverter(dto -> new SpacePurgeTask(purge))
                .toDTOConverter((task, typeName) -> new SpacePurgeTaskDTO(typeName))
                .typeName(TASK_TYPE.asString())
                .withFactory(TaskDTOModule::new);
        }

        @Override
        public String getType() {
            return type;
        }
    }

    public record DetailsDTO(@JsonProperty("type") String type,
                             @JsonProperty("timestamp") Instant timestamp,
                             @JsonProperty("deletedTeamMailboxes") long deletedTeamMailboxes,
                             @JsonProperty("failedTeamMailboxes") List<String> failedTeamMailboxes) implements AdditionalInformationDTO {
        public static final AdditionalInformationDTOModule<Details, DetailsDTO> SERIALIZATION_MODULE =
            DTOModule.forDomainObject(Details.class)
                .convertToDTO(DetailsDTO.class)
                .toDomainObjectConverter(dto -> new Details(dto.timestamp(), dto.deletedTeamMailboxes(), dto.failedTeamMailboxes()))
                .toDTOConverter((details, typeName) -> new DetailsDTO(typeName, details.timestamp(), details.deletedTeamMailboxes(),
                    details.failedTeamMailboxes()))
                .typeName(TASK_TYPE.asString())
                .withFactory(AdditionalInformationDTOModule::new);

        @Override
        public String getType() {
            return type;
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }
    }

    private final SpacePurge purge;
    private final SpacePurge.Context context;

    public SpacePurgeTask(SpacePurge purge) {
        this.purge = purge;
        this.context = new SpacePurge.Context();
    }

    @Override
    public Result run() {
        return purge.purge(context).block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        SpacePurge.Context.Snapshot snapshot = context.snapshot();
        return Optional.of(new Details(Clock.systemUTC().instant(), snapshot.deletedTeamMailboxes(), snapshot.failedTeamMailboxes()));
    }
}
