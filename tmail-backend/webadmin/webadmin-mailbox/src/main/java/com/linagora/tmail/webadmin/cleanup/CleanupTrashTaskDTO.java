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

package com.linagora.tmail.webadmin.cleanup;

import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CleanupTrashTaskDTO(@JsonProperty("type") String type, @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) implements TaskDTO {

    public static TaskDTOModule<CleanupTrashTask, CleanupTrashTaskDTO> module(CleanupService service) {
        return DTOModule.forDomainObject(CleanupTrashTask.class)
            .convertToDTO(CleanupTrashTaskDTO.class)
            .toDomainObjectConverter(dto -> new CleanupTrashTask(service,
                dto.runningOptions.map(RunningOptionsDTO::asDomainObject).orElse(RunningOptions.DEFAULT)))
            .toDTOConverter(((domainObject, typeName) ->
                new CleanupTrashTaskDTO(typeName, Optional.of(RunningOptionsDTO.asDTO(domainObject.getRunningOptions())))))
            .typeName(CleanupTrashTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    public String getType() {
        return type;
    }
}
