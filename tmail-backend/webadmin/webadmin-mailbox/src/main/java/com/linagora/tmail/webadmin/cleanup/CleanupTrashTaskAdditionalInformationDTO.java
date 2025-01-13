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

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public record CleanupTrashTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                       @JsonProperty("processedUsersCount") long processedUsersCount,
                                                       @JsonProperty("deletedMessagesCount") long deletedMessagesCount,
                                                       @JsonProperty("failedUsers") ImmutableList<String> failedUsers,
                                                       @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions,
                                                       @JsonProperty("timestamp") Instant timestamp) implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<CleanupTrashTaskDetails, CleanupTrashTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(CleanupTrashTaskDetails.class)
            .convertToDTO(CleanupTrashTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(CleanupTrashTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(CleanupTrashTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(CleanupTrashTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static CleanupTrashTaskAdditionalInformationDTO fromDomainObject(CleanupTrashTaskDetails details, String type) {
        return new CleanupTrashTaskAdditionalInformationDTO(type,
            details.processedUsersCount(),
            details.deletedMessagesCount(),
            details.failedUsers(),
            Optional.of(RunningOptionsDTO.asDTO(details.runningOptions())),
            details.timestamp());
    }

    private CleanupTrashTaskDetails toDomainObject() {
        return new CleanupTrashTaskDetails(timestamp,
            processedUsersCount,
            deletedMessagesCount,
            failedUsers,
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
