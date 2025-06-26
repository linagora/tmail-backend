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

import java.time.Instant;
import java.util.List;

import org.apache.james.core.Domain;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                      @JsonProperty("timestamp") Instant timestamp,
                                                                      @JsonProperty("domain") String domain,
                                                                      @JsonProperty("processedQuotaRoots") long processedQuotaRoots,
                                                                      @JsonProperty("failedQuotaRoots") List<String> failedQuotaRoots) implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<RecomputeQuotaTeamMailboxesTask.Details, RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(RecomputeQuotaTeamMailboxesTask.Details.class)
            .convertToDTO(RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto ->
                new RecomputeQuotaTeamMailboxesTask.Details(
                    dto.timestamp(),
                    Domain.of(dto.domain()),
                    dto.processedQuotaRoots(),
                    dto.failedQuotaRoots()))
            .toDTOConverter(((domainObject, typeName) ->
                new RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO(
                    typeName, domainObject.timestamp(), domainObject.getDomain().asString(), domainObject.getProcessedQuotaRoots(),
                    domainObject.getFailedQuotaRoots())))
            .typeName(RecomputeQuotaTeamMailboxesTask.TASK_TYPE.asString())
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
