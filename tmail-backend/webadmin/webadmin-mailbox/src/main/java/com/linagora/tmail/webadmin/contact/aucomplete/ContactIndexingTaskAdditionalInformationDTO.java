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

package com.linagora.tmail.webadmin.contact.aucomplete;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingTask.Details;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingTask.RunningOptions;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingTaskDTO.RunningOptionsDTO;

public record ContactIndexingTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                          @JsonProperty("processedUsersCount") long processedUsersCount,
                                                          @JsonProperty("indexedContactsCount") long indexedContactsCount,
                                                          @JsonProperty("failedContactsCount") long failedContactsCount,
                                                          @JsonProperty("failedUsers") ImmutableList<String> failedUsers,
                                                          @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions,
                                                          @JsonProperty("timestamp") Instant timestamp) implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<Details, ContactIndexingTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(Details.class)
            .convertToDTO(ContactIndexingTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(ContactIndexingTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(ContactIndexingTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(ContactIndexingTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static ContactIndexingTaskAdditionalInformationDTO fromDomainObject(Details details, String type) {
        return new ContactIndexingTaskAdditionalInformationDTO(type,
            details.processedUsersCount(),
            details.indexedContactsCount(),
            details.failedContactsCount(),
            details.failedUsers(),
            Optional.of(ContactIndexingTaskDTO.RunningOptionsDTO.asDTO(details.runningOptions())),
            details.timestamp());
    }

    private Details toDomainObject() {
        return new Details(timestamp,
            processedUsersCount,
            indexedContactsCount,
            failedContactsCount,
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
