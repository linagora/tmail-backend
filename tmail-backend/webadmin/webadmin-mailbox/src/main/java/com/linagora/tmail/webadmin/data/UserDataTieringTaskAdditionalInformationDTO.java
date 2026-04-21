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

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.webadmin.data.UserDataTieringTaskDTO.RunningOptionsDTO;

public record UserDataTieringTaskAdditionalInformationDTO(
    @JsonProperty("type") String type,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("username") String username,
    @JsonProperty("tieringSeconds") long tieringSeconds,
    @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions,
    @JsonProperty("tieredMessageCount") long tieredMessageCount,
    @JsonProperty("failedMessageCount") long failedMessageCount
) implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<UserDataTieringTask.AdditionalInformation, UserDataTieringTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(UserDataTieringTask.AdditionalInformation.class)
            .convertToDTO(UserDataTieringTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(UserDataTieringTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(UserDataTieringTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(UserDataTieringTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static UserDataTieringTaskAdditionalInformationDTO fromDomainObject(UserDataTieringTask.AdditionalInformation info, String type) {
        return new UserDataTieringTaskAdditionalInformationDTO(
            type,
            info.timestamp(),
            info.username(),
            info.tieringSeconds(),
            Optional.of(RunningOptionsDTO.of(info.runningOptions())),
            info.tieredMessageCount(),
            info.failedMessageCount());
    }

    private UserDataTieringTask.AdditionalInformation toDomainObject() {
        return new UserDataTieringTask.AdditionalInformation(
            timestamp,
            username,
            tieringSeconds,
            runningOptions.map(RunningOptionsDTO::asDomainObject).orElse(RunningOptions.DEFAULT),
            tieredMessageCount,
            failedMessageCount);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}
