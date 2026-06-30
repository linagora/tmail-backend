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

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public record UserTemplatesProvisionTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                @JsonProperty("timestamp") Instant timestamp,
                                                                @JsonProperty("sourceUser") String sourceUser,
                                                                @JsonProperty("targetUser") String targetUser,
                                                                @JsonProperty("folderName") String folderName,
                                                                @JsonProperty("overwriteExisting") boolean overwriteExisting,
                                                                @JsonProperty("prune") boolean prune,
                                                                @JsonProperty("processedUsers") long processedUsers,
                                                                @JsonProperty("appliedTemplates") long appliedTemplates,
                                                                @JsonProperty("skippedTemplates") long skippedTemplates,
                                                                @JsonProperty("removedTemplates") long removedTemplates,
                                                                @JsonProperty("failedUsers") ImmutableList<String> failedUsers) implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<UserTemplatesProvisionTask.AdditionalInformation, UserTemplatesProvisionTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(UserTemplatesProvisionTask.AdditionalInformation.class)
            .convertToDTO(UserTemplatesProvisionTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(UserTemplatesProvisionTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(UserTemplatesProvisionTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(UserTemplatesProvisionTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static UserTemplatesProvisionTaskAdditionalInformationDTO fromDomainObject(UserTemplatesProvisionTask.AdditionalInformation info, String type) {
        return new UserTemplatesProvisionTaskAdditionalInformationDTO(type,
            info.timestamp(),
            info.sourceUser(),
            info.targetUser(),
            info.folderName(),
            info.overwriteExisting(),
            info.prune(),
            info.processedUsers(),
            info.appliedTemplates(),
            info.skippedTemplates(),
            info.removedTemplates(),
            info.failedUsers());
    }

    private UserTemplatesProvisionTask.AdditionalInformation toDomainObject() {
        return new UserTemplatesProvisionTask.AdditionalInformation(timestamp,
            sourceUser,
            targetUser,
            folderName,
            overwriteExisting,
            prune,
            processedUsers,
            appliedTemplates,
            skippedTemplates,
            removedTemplates,
            failedUsers);
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
