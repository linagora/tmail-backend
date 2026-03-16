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

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PopulateKeywordEmailQueryViewTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                        @JsonProperty("processedUserCount") long processedUserCount,
                                                                        @JsonProperty("processedMessageCount") long processedMessageCount,
                                                                        @JsonProperty("provisionedKeywordViewCount") long provisionedKeywordViewCount,
                                                                        @JsonProperty("failedUserCount") long failedUserCount,
                                                                        @JsonProperty("failedMessageCount") long failedMessageCount,
                                                                        @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions,
                                                                        @JsonProperty("timestamp") Instant timestamp) implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<PopulateKeywordEmailQueryViewTask.AdditionalInformation, PopulateKeywordEmailQueryViewTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(PopulateKeywordEmailQueryViewTask.AdditionalInformation.class)
            .convertToDTO(PopulateKeywordEmailQueryViewTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(PopulateKeywordEmailQueryViewTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(PopulateKeywordEmailQueryViewTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(PopulateKeywordEmailQueryViewTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static PopulateKeywordEmailQueryViewTaskAdditionalInformationDTO fromDomainObject(PopulateKeywordEmailQueryViewTask.AdditionalInformation details, String type) {
        return new PopulateKeywordEmailQueryViewTaskAdditionalInformationDTO(
            type,
            details.processedUserCount(),
            details.processedMessageCount(),
            details.provisionedKeywordViewCount(),
            details.failedUserCount(),
            details.failedMessageCount(),
            Optional.of(RunningOptionsDTO.asDTO(details.runningOptions())),
            details.timestamp());
    }

    private PopulateKeywordEmailQueryViewTask.AdditionalInformation toDomainObject() {
        return new PopulateKeywordEmailQueryViewTask.AdditionalInformation(
            timestamp,
            processedUserCount,
            processedMessageCount,
            provisionedKeywordViewCount,
            failedUserCount,
            failedMessageCount,
            runningOptions.map(RunningOptionsDTO::asDomainObject).orElse(RunningOptions.DEFAULT));
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
