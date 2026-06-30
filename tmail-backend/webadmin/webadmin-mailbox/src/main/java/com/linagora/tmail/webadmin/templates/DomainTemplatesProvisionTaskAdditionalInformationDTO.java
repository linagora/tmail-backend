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

public record DomainTemplatesProvisionTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                  @JsonProperty("timestamp") Instant timestamp,
                                                                  @JsonProperty("domain") String domain,
                                                                  @JsonProperty("sourceUser") String sourceUser,
                                                                  @JsonProperty("folderName") String folderName,
                                                                  @JsonProperty("overwriteExisting") boolean overwriteExisting,
                                                                  @JsonProperty("prune") boolean prune,
                                                                  @JsonProperty("usersPerSecond") int usersPerSecond,
                                                                  @JsonProperty("processedUsers") long processedUsers,
                                                                  @JsonProperty("appliedTemplates") long appliedTemplates,
                                                                  @JsonProperty("skippedTemplates") long skippedTemplates,
                                                                  @JsonProperty("removedTemplates") long removedTemplates,
                                                                  @JsonProperty("failedUsers") ImmutableList<String> failedUsers) implements AdditionalInformationDTO {
    public static AdditionalInformationDTOModule<DomainTemplatesProvisionTask.AdditionalInformation, DomainTemplatesProvisionTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(DomainTemplatesProvisionTask.AdditionalInformation.class)
            .convertToDTO(DomainTemplatesProvisionTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(DomainTemplatesProvisionTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(DomainTemplatesProvisionTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(DomainTemplatesProvisionTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static DomainTemplatesProvisionTaskAdditionalInformationDTO fromDomainObject(DomainTemplatesProvisionTask.AdditionalInformation info, String type) {
        return new DomainTemplatesProvisionTaskAdditionalInformationDTO(type,
            info.timestamp(),
            info.domain(),
            info.sourceUser(),
            info.folderName(),
            info.overwriteExisting(),
            info.prune(),
            info.usersPerSecond(),
            info.processedUsers(),
            info.appliedTemplates(),
            info.skippedTemplates(),
            info.removedTemplates(),
            info.failedUsers());
    }

    private DomainTemplatesProvisionTask.AdditionalInformation toDomainObject() {
        return new DomainTemplatesProvisionTask.AdditionalInformation(timestamp,
            domain,
            sourceUser,
            folderName,
            overwriteExisting,
            prune,
            usersPerSecond,
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
