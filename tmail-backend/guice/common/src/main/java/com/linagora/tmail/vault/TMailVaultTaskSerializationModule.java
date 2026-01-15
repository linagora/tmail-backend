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

package com.linagora.tmail.vault;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultDeleteTask;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultDeleteTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultDeleteTaskDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultExportTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultExportTaskDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTaskDTO;
import org.apache.james.webadmin.vault.routes.WebAdminDeletedMessagesVaultDeleteTaskAdditionalInformationDTO;
import org.apache.james.webadmin.vault.routes.WebAdminDeletedMessagesVaultRestoreTaskAdditionalInformationDTO;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.linagora.tmail.vault.blob.TmailBlobStoreVaultGarbageCollectionTask;
import com.linagora.tmail.vault.blob.TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO;
import com.linagora.tmail.vault.blob.TmailBlobStoreVaultGarbageCollectionTaskDTO;

public class TMailVaultTaskSerializationModule extends AbstractModule {
    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> tmailBlobStoreVaultGarbageCollectionTask(TmailBlobStoreVaultGarbageCollectionTask.Factory factory) {
        return TmailBlobStoreVaultGarbageCollectionTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> deletedMessagesVaultDeleteTask(DeletedMessagesVaultDeleteTask.Factory factory) {
        return DeletedMessagesVaultDeleteTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> deletedMessagesVaultExportTask(DeletedMessagesVaultExportTaskDTO.Factory factory) {
        return DeletedMessagesVaultExportTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> deletedMessagesVaultRestoreTask(DeletedMessagesVaultRestoreTaskDTO.Factory factory) {
        return DeletedMessagesVaultRestoreTaskDTO.module(factory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> tmailBlobStoreVaultGarbageCollectionAdditionalInformation() {
        return TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminTmailBlobStoreVaultGarbageCollectionAdditionalInformation() {
        return TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> deletedMessagesVaultDeleteAdditionalInformation(MessageId.Factory factory) {
        return DeletedMessagesVaultDeleteTaskAdditionalInformationDTO.module(factory);
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminDeletedMessagesVaultDeleteAdditionalInformation(MessageId.Factory factory) {
        return WebAdminDeletedMessagesVaultDeleteTaskAdditionalInformationDTO.module(factory);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> deletedMessagesVaultExportAdditionalInformation() {
        return DeletedMessagesVaultExportTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminDeletedMessagesVaultExportAdditionalInformation() {
        return DeletedMessagesVaultExportTaskAdditionalInformationDTO.module();
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> deletedMessagesVaultRestoreAdditionalInformation() {
        return DeletedMessagesVaultRestoreTaskAdditionalInformationDTO.module();
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminDeletedMessagesVaultRestoreAdditionalInformation() {
        return WebAdminDeletedMessagesVaultRestoreTaskAdditionalInformationDTO.module();
    }
}
