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

package com.linagora.tmail.webadmin.quota.recompute;

import org.apache.james.core.Domain;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RecomputeQuotaTeamMailboxesTaskDTO(@JsonProperty("type") String type,
                                                 @JsonProperty("domain") String domain) implements TaskDTO {
    public static TaskDTOModule<RecomputeQuotaTeamMailboxesTask, RecomputeQuotaTeamMailboxesTaskDTO> module(RecomputeQuotaTeamMailboxesService service) {
        return DTOModule.forDomainObject(RecomputeQuotaTeamMailboxesTask.class)
            .convertToDTO(RecomputeQuotaTeamMailboxesTaskDTO.class)
            .toDomainObjectConverter(dto -> new RecomputeQuotaTeamMailboxesTask(service, Domain.of(dto.domain())))
            .toDTOConverter(((domainObject, typeName) ->
                new RecomputeQuotaTeamMailboxesTaskDTO(typeName, domainObject.getTeamMailboxDomain().asString())))
            .typeName(RecomputeQuotaTeamMailboxesTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    public String getType() {
        return type;
    }
}
