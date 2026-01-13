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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;

import org.apache.james.blob.api.BucketName;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.Task;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableSet;

class TmailBlobStoreVaultGarbageCollectionTaskSerializationTest {
   private static final TmailBlobStoreDeletedMessageVault DELETED_MESSAGE_VAULT = Mockito.mock(TmailBlobStoreDeletedMessageVault.class);
   private static final TmailBlobStoreVaultGarbageCollectionTask.Factory TASK_FACTORY = new TmailBlobStoreVaultGarbageCollectionTask.Factory(DELETED_MESSAGE_VAULT);

    private static final JsonTaskSerializer TASK_SERIALIZER = JsonTaskSerializer.of(TmailBlobStoreVaultGarbageCollectionTaskDTO.module(TASK_FACTORY));
    private static final ZonedDateTime BEGINNING_OF_RETENTION_PERIOD = ZonedDateTime.parse("2019-09-03T15:26:13.356+02:00[Europe/Paris]");
    private static final ImmutableSet<BucketName> BUCKET_IDS = ImmutableSet.of(BucketName.of("1"), BucketName.of("2"), BucketName.of("3"));
    private static final int DELETED_BLOBS = 5;
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final TmailBlobStoreVaultGarbageCollectionTask.AdditionalInformation DETAILS = new TmailBlobStoreVaultGarbageCollectionTask.AdditionalInformation(BEGINNING_OF_RETENTION_PERIOD, BUCKET_IDS, DELETED_BLOBS, TIMESTAMP);
    private static final TmailBlobStoreVaultGarbageCollectionTask TASK = TASK_FACTORY.create();

    private static final String SERIALIZED_TASK = "{\"type\":\"tmail-deleted-messages-blob-store-based-garbage-collection\"}";
    private static final String SERIALIZED_ADDITIONAL_INFORMATION_TASK = "{\"type\":\"tmail-deleted-messages-blob-store-based-garbage-collection\", \"beginningOfRetentionPeriod\":\"2019-09-03T15:26:13.356+02:00[Europe/Paris]\",\"deletedBuckets\":[\"1\", \"2\", \"3\"], \"deletedBlobs\":5, \"timestamp\": \"2018-11-13T12:00:55Z\"}";

    private static final JsonTaskAdditionalInformationSerializer JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER = JsonTaskAdditionalInformationSerializer.of(TmailBlobStoreVaultGarbageCollectionTaskAdditionalInformationDTO.module());

    @BeforeAll
    static void setUp() {
        Mockito.when(DELETED_MESSAGE_VAULT.getBeginningOfRetentionPeriod())
            .thenReturn(BEGINNING_OF_RETENTION_PERIOD);
    }

    @Test
    void taskShouldBeSerializable() throws JsonProcessingException {
        assertThatJson(TASK_SERIALIZER.serialize(TASK))
            .isEqualTo(SERIALIZED_TASK);
    }

    @Test
    void taskShouldBeDeserializable() throws IOException {
        Task deserialized = TASK_SERIALIZER.deserialize(SERIALIZED_TASK);

        assertThat(deserialized).isInstanceOf(TmailBlobStoreVaultGarbageCollectionTask.class);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        assertThatJson(JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER.serialize(DETAILS)).isEqualTo(SERIALIZED_ADDITIONAL_INFORMATION_TASK);
    }

    @Test
    void additionalInformationShouldBeDeserializable() throws IOException {
        assertThat(JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER.deserialize(SERIALIZED_ADDITIONAL_INFORMATION_TASK))
            .usingRecursiveComparison()
            .isEqualTo(DETAILS);
    }
}