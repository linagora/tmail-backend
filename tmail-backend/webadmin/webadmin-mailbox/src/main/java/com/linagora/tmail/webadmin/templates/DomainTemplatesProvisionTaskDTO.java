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

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DomainTemplatesProvisionTaskDTO(@JsonProperty("type") String type,
                                              @JsonProperty("domain") String domain,
                                              @JsonProperty("sourceUser") String sourceUser,
                                              @JsonProperty("folderName") String folderName,
                                              @JsonProperty("overwriteExisting") boolean overwriteExisting,
                                              @JsonProperty("prune") boolean prune,
                                              @JsonProperty("usersPerSecond") int usersPerSecond) implements TaskDTO {
    public static TaskDTOModule<DomainTemplatesProvisionTask, DomainTemplatesProvisionTaskDTO> module(TemplatesProvisionService service) {
        return DTOModule.forDomainObject(DomainTemplatesProvisionTask.class)
            .convertToDTO(DomainTemplatesProvisionTaskDTO.class)
            .toDomainObjectConverter(dto -> new DomainTemplatesProvisionTask(service,
                Domain.of(dto.domain()),
                new TemplatingSource(Username.of(dto.sourceUser()), dto.folderName()),
                new ProvisionOptions(dto.overwriteExisting(), dto.prune()),
                dto.usersPerSecond()))
            .toDTOConverter((task, type) -> new DomainTemplatesProvisionTaskDTO(type,
                task.getDomain().asString(),
                task.getSourceUser().asString(),
                task.getFolderName(),
                task.getOptions().overwriteExisting(),
                task.getOptions().prune(),
                task.getUsersPerSecond()))
            .typeName(DomainTemplatesProvisionTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    public String getType() {
        return type;
    }
}
