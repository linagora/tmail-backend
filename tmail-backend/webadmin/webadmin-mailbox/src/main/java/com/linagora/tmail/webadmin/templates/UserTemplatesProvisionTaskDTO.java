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

package com.linagora.tmail.webadmin.templates;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserTemplatesProvisionTaskDTO(@JsonProperty("type") String type,
                                            @JsonProperty("sourceUser") String sourceUser,
                                            @JsonProperty("targetUser") String targetUser,
                                            @JsonProperty("folderName") String folderName,
                                            @JsonProperty("overwriteExisting") boolean overwriteExisting,
                                            @JsonProperty("prune") boolean prune) implements TaskDTO {
    public static TaskDTOModule<UserTemplatesProvisionTask, UserTemplatesProvisionTaskDTO> module(TemplatesProvisionService service) {
        return DTOModule.forDomainObject(UserTemplatesProvisionTask.class)
            .convertToDTO(UserTemplatesProvisionTaskDTO.class)
            .toDomainObjectConverter(dto -> new UserTemplatesProvisionTask(service,
                Username.of(dto.sourceUser()),
                Username.of(dto.targetUser()),
                dto.folderName(),
                new ProvisionOptions(dto.overwriteExisting(), dto.prune())))
            .toDTOConverter((task, type) -> new UserTemplatesProvisionTaskDTO(type,
                task.getSourceUser().asString(),
                task.getTargetUser().asString(),
                task.getFolderName(),
                task.getOptions().overwriteExisting(),
                task.getOptions().prune()))
            .typeName(UserTemplatesProvisionTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    public String getType() {
        return type;
    }
}
