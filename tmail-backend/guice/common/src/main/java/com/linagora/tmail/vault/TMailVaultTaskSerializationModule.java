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

import org.apache.james.modules.vault.VaultTaskSerializationModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.dto.DTOModuleInjections;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.linagora.tmail.vault.blob.TmailBlobStoreVaultGarbageCollectionTask;
import com.linagora.tmail.vault.blob.TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO;
import com.linagora.tmail.vault.blob.TmailBlobStoreVaultGarbageCollectionTaskDTO;

public class TMailVaultTaskSerializationModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new VaultTaskSerializationModule());
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> tmailBlobStoreVaultGarbageCollectionTask(TmailBlobStoreVaultGarbageCollectionTask.Factory factory) {
        return TmailBlobStoreVaultGarbageCollectionTaskDTO.module(factory);
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
}
