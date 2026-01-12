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

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;

import org.apache.james.blob.api.BucketName;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;

public class TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    static TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO fromDomainObject(TmailBlobStoreVaultGarbageCollectionTask.AdditionalInformation additionalInformation, String type) {
        return new TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO(
            type,
            additionalInformation.getBeginningOfRetentionPeriod().toString(),
            additionalInformation.getDeletedBuckets(),
            additionalInformation.getDeletedBlobs(),
            additionalInformation.timestamp()
        );
    }

    public static final AdditionalInformationDTOModule<TmailBlobStoreVaultGarbageCollectionTask.AdditionalInformation, TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(TmailBlobStoreVaultGarbageCollectionTask.AdditionalInformation.class)
            .convertToDTO(TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(TmailBlobStoreVaultGarbageCollectionTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    public static final AdditionalInformationDTOModule<TmailBlobStoreVaultGarbageCollectionTask.AdditionalInformation, TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(TmailBlobStoreVaultGarbageCollectionTask.AdditionalInformation.class)
            .convertToDTO(TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(TmailBlobStoreVaultGarbageCollectionTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String beginningOfRetentionPeriod;
    private final Collection<String> deletedBuckets;
    private final int deletedBlobs;
    private final String type;
    private final Instant timestamp;

    TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO(
        @JsonProperty("type") String type,
        @JsonProperty("beginningOfRetentionPeriod") String beginningOfRetentionPeriod,
        @JsonProperty("deletedBuckets") Collection<String> deletedBuckets,
        @JsonProperty("deletedBlobs") int deletedBlobs,
        @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.beginningOfRetentionPeriod = beginningOfRetentionPeriod;
        this.deletedBuckets = deletedBuckets;
        this.deletedBlobs = deletedBlobs;
        this.timestamp = timestamp;
    }

    TmailBlobStoreVaultGarbageCollectionTask.AdditionalInformation toDomainObject() {
        return new TmailBlobStoreVaultGarbageCollectionTask.AdditionalInformation(
            ZonedDateTime.parse(beginningOfRetentionPeriod),
            deletedBuckets
                .stream()
                .map(BucketName::of)
                .collect(ImmutableSet.toImmutableSet()),
            deletedBlobs,
            timestamp);
    }

    public String getBeginningOfRetentionPeriod() {
        return beginningOfRetentionPeriod;
    }

    public Collection<String> getDeletedBuckets() {
        return deletedBuckets;
    }

    public int getDeletedBlobs() {
        return deletedBlobs;
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
