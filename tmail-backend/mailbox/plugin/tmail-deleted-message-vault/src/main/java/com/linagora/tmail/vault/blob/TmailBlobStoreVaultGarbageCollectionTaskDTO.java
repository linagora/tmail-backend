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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.vault.blob;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TmailBlobStoreVaultGarbageCollectionTaskDTO implements TaskDTO {
    static TmailBlobStoreVaultGarbageCollectionTaskDTO fromDomainObject(TmailBlobStoreVaultGarbageCollectionTask task, String type) {
        return new TmailBlobStoreVaultGarbageCollectionTaskDTO(type);
    }

    public static TaskDTOModule<TmailBlobStoreVaultGarbageCollectionTask, TmailBlobStoreVaultGarbageCollectionTaskDTO> module(TmailBlobStoreVaultGarbageCollectionTask.Factory factory) {
        return DTOModule
            .forDomainObject(TmailBlobStoreVaultGarbageCollectionTask.class)
            .convertToDTO(TmailBlobStoreVaultGarbageCollectionTaskDTO.class)
            .toDomainObjectConverter(dto -> TmailBlobStoreVaultGarbageCollectionTaskDTO.toDomainObject(factory))
            .toDTOConverter(TmailBlobStoreVaultGarbageCollectionTaskDTO::fromDomainObject)
            .typeName(TmailBlobStoreVaultGarbageCollectionTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }


    private final String type;

    TmailBlobStoreVaultGarbageCollectionTaskDTO(@JsonProperty("type") String type) {
        this.type = type;
    }

    private static TmailBlobStoreVaultGarbageCollectionTask toDomainObject(TmailBlobStoreVaultGarbageCollectionTask.Factory factory) {
        return factory.create();
    }

    @Override
    public String getType() {
        return type;
    }

}
