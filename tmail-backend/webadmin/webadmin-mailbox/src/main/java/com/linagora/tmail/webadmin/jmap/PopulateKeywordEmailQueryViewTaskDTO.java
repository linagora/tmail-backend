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

package com.linagora.tmail.webadmin.jmap;

import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PopulateKeywordEmailQueryViewTaskDTO(@JsonProperty("type") String type,
                                                   @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) implements TaskDTO {

    public static PopulateKeywordEmailQueryViewTaskDTO of(String typeName, RunningOptions runningOptions) {
        return new PopulateKeywordEmailQueryViewTaskDTO(typeName, Optional.of(RunningOptionsDTO.asDTO(runningOptions)));
    }

    public static TaskDTOModule<PopulateKeywordEmailQueryViewTask, PopulateKeywordEmailQueryViewTaskDTO> module(KeywordEmailQueryViewPopulator populator) {
        return DTOModule.forDomainObject(PopulateKeywordEmailQueryViewTask.class)
            .convertToDTO(PopulateKeywordEmailQueryViewTaskDTO.class)
            .toDomainObjectConverter(dto -> new PopulateKeywordEmailQueryViewTask(populator,
                dto.runningOptions().map(RunningOptionsDTO::asDomainObject).orElse(RunningOptions.DEFAULT)))
            .toDTOConverter((domainObject, typeName) -> PopulateKeywordEmailQueryViewTaskDTO.of(typeName, domainObject.getRunningOptions()))
            .typeName(PopulateKeywordEmailQueryViewTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    public String getType() {
        return type;
    }
}
