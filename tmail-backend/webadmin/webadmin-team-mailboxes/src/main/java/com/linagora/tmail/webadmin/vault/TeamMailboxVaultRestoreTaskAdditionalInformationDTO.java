/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                  *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.webadmin.vault;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamMailboxVaultRestoreTaskAdditionalInformationDTO(
    @JsonProperty("type") String type,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("teamMailboxAddress") String teamMailboxAddress,
    @JsonProperty("successfulRestoreCount") long successfulRestoreCount,
    @JsonProperty("errorRestoreCount") long errorRestoreCount) implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<TeamMailboxVaultRestoreTask.AdditionalInformation, TeamMailboxVaultRestoreTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(TeamMailboxVaultRestoreTask.AdditionalInformation.class)
            .convertToDTO(TeamMailboxVaultRestoreTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new TeamMailboxVaultRestoreTask.AdditionalInformation(
                dto.teamMailboxAddress(),
                dto.successfulRestoreCount(),
                dto.errorRestoreCount(),
                dto.timestamp()))
            .toDTOConverter((domainObject, typeName) -> new TeamMailboxVaultRestoreTaskAdditionalInformationDTO(
                typeName,
                domainObject.timestamp(),
                domainObject.getTeamMailboxAddress(),
                domainObject.getSuccessfulRestoreCount(),
                domainObject.getErrorRestoreCount()))
            .typeName(TeamMailboxVaultRestoreTask.TYPE.asString())
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
