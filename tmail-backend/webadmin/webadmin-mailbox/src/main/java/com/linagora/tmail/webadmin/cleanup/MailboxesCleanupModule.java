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

import static org.apache.james.webadmin.routes.MailboxesRoutes.ALL_MAILBOXES_TASKS;

import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class MailboxesCleanupModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), TaskFromRequestRegistry.TaskRegistration.class, Names.named(ALL_MAILBOXES_TASKS))
            .addBinding()
            .to(CleanupTrashTaskRegistration.class);

        Multibinder.newSetBinder(binder(), TaskFromRequestRegistry.TaskRegistration.class, Names.named(ALL_MAILBOXES_TASKS))
            .addBinding()
            .to(CleanupSpamTaskRegistration.class);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> cleanupTrashTaskDTO(CleanupService cleanupService) {
        return CleanupTrashTaskDTO.module(cleanupService);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> cleanupSpamTaskDTO(CleanupService cleanupService) {
        return CleanupSpamTaskDTO.module(cleanupService);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> cleanupTrashTaskAdditionalInformation() {
        return CleanupTrashTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminCleanupTrashTaskAdditionalInformation() {
        return CleanupTrashTaskAdditionalInformationDTO.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> cleanupSpamTaskAdditionalInformation() {
        return CleanupSpamTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminCleanupSpamTaskAdditionalInformation() {
        return CleanupSpamTaskAdditionalInformationDTO.module();
    }
}
