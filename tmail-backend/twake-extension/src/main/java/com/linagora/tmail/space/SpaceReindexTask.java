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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Indexes the team mailboxes bound to a space, for the spaces bound before the {@link SpaceIndex} existed. */
public class SpaceReindexTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("twake-space-reindex");

    public record Details(Instant timestamp, long indexedSpaces) implements TaskExecutionDetails.AdditionalInformation {
    }

    public record SpaceReindexTaskDTO(@JsonProperty("type") String type) implements TaskDTO {
        public static TaskDTOModule<SpaceReindexTask, SpaceReindexTaskDTO> module(SpaceReconciler reconciler) {
            return DTOModule.forDomainObject(SpaceReindexTask.class)
                .convertToDTO(SpaceReindexTaskDTO.class)
                .toDomainObjectConverter(dto -> new SpaceReindexTask(reconciler))
                .toDTOConverter((task, typeName) -> new SpaceReindexTaskDTO(typeName))
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
                             @JsonProperty("indexedSpaces") long indexedSpaces) implements AdditionalInformationDTO {
        public static final AdditionalInformationDTOModule<Details, DetailsDTO> SERIALIZATION_MODULE =
            DTOModule.forDomainObject(Details.class)
                .convertToDTO(DetailsDTO.class)
                .toDomainObjectConverter(dto -> new Details(dto.timestamp(), dto.indexedSpaces()))
                .toDTOConverter((details, typeName) -> new DetailsDTO(typeName, details.timestamp(), details.indexedSpaces()))
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

    private final SpaceReconciler reconciler;
    private final AtomicLong indexedSpaces = new AtomicLong();

    public SpaceReindexTask(SpaceReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Override
    public Result run() {
        return reconciler.reindex(indexedSpaces::incrementAndGet)
            .thenReturn(Result.COMPLETED)
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new Details(Clock.systemUTC().instant(), indexedSpaces.get()));
    }
}
