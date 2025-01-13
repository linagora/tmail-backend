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

package com.linagora.tmail.webadmin.archival;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.core.Username;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InboxArchivalTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    
    public static final AdditionalInformationDTOModule<InboxArchivalTask.AdditionalInformation, InboxArchivalTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(InboxArchivalTask.AdditionalInformation.class)
            .convertToDTO(InboxArchivalTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(InboxArchivalTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(InboxArchivalTaskAdditionalInformationDTO::toDto)
            .typeName(InboxArchivalTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private static InboxArchivalTask.AdditionalInformation toDomainObject(InboxArchivalTaskAdditionalInformationDTO dto) {
        return new InboxArchivalTask.AdditionalInformation(dto.timestamp,
            dto.archivedMessageCount,
            dto.errorMessageCount,
            dto.getSuccessfulUsersCount(),
            dto.getFailedUsersCount(),
            toSetUsername(dto.failedUsers));
    }

    private static InboxArchivalTaskAdditionalInformationDTO toDto(InboxArchivalTask.AdditionalInformation domainObject, String type) {
        return new InboxArchivalTaskAdditionalInformationDTO(type,
            domainObject.timestamp(),
            domainObject.successfulUsersCount(),
            domainObject.failedUsersCount(),
            toSetString(domainObject.failedUsers()),
            domainObject.archivedMessageCount(),
            domainObject.errorMessageCount());
    }

    private static Set<Username> toSetUsername(Set<String> usernames) {
        return usernames.stream()
            .map(Username::of)
            .collect(Collectors.toSet());
    }

    private static Set<String> toSetString(Set<Username> usernames) {
        return usernames.stream()
            .map(Username::asString)
            .collect(Collectors.toSet());
    }

    private final String type;
    private final Instant timestamp;
    private final long successfulUsersCount;
    private final long failedUsersCount;
    private final Set<String> failedUsers;
    private final long archivedMessageCount;
    private final long errorMessageCount;

    public InboxArchivalTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                     @JsonProperty("timestamp") Instant timestamp,
                                                     @JsonProperty("successfulUsersCount") long successfulUsersCount,
                                                     @JsonProperty("failedUsersCount") long failedUsersCount,
                                                     @JsonProperty("failedUsers") Set<String> failedUsers,
                                                     @JsonProperty("archivedMessageCount") long archivedMessageCount,
                                                     @JsonProperty("errorMessageCount") long errorMessageCount) {
        this.type = type;
        this.timestamp = timestamp;
        this.successfulUsersCount = successfulUsersCount;
        this.failedUsersCount = failedUsersCount;
        this.failedUsers = failedUsers;
        this.archivedMessageCount = archivedMessageCount;
        this.errorMessageCount = errorMessageCount;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public long getArchivedMessageCount() {
        return archivedMessageCount;
    }

    public long getErrorMessageCount() {
        return errorMessageCount;
    }

    public long getSuccessfulUsersCount() {
        return successfulUsersCount;
    }

    public long getFailedUsersCount() {
        return failedUsersCount;
    }

    public Set<String> getFailedUsers() {
        return failedUsers;
    }
}
