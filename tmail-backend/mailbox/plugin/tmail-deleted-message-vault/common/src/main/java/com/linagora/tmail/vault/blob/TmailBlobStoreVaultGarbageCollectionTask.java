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

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BucketName;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class TmailBlobStoreVaultGarbageCollectionTask implements Task {

    static class TmailBlobStoreVaultGarbageCollectionContext {
        private final Collection<BucketName> deletedBuckets;
        private final AtomicInteger deletedBlobs;

        TmailBlobStoreVaultGarbageCollectionContext() {
            this.deletedBuckets = new ConcurrentLinkedQueue<>();
            this.deletedBlobs = new AtomicInteger(0);
        }

        void recordDeletedBlobSuccess() {
            deletedBlobs.incrementAndGet();
        }

        int deletedBlobsCount() {
            return deletedBlobs.get();
        }

        void recordDeletedBucketSuccess(BucketName bucketName) {
            deletedBuckets.add(bucketName);
        }

        ImmutableSet<BucketName> getDeletedBuckets() {
            return ImmutableSet.copyOf(deletedBuckets);
        }
    }

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final ZonedDateTime beginningOfRetentionPeriod;
        private final ImmutableSet<BucketName> deletedBuckets;
        private final int deletedBlobs;
        private final Instant timestamp;

        AdditionalInformation(ZonedDateTime beginningOfRetentionPeriod, ImmutableSet<BucketName> deletedBuckets, int deletedBlobs, Instant timestamp) {
            this.beginningOfRetentionPeriod = beginningOfRetentionPeriod;
            this.deletedBuckets = deletedBuckets;
            this.deletedBlobs = deletedBlobs;
            this.timestamp = timestamp;
        }

        public ZonedDateTime getBeginningOfRetentionPeriod() {
            return beginningOfRetentionPeriod;
        }

        public List<String> getDeletedBuckets() {
            return deletedBuckets.stream()
                .map(BucketName::asString)
                .collect(ImmutableList.toImmutableList());
        }

        public int getDeletedBlobs() {
            return deletedBlobs;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    static final TaskType TYPE = TaskType.of("tmail-deleted-messages-blob-store-based-garbage-collection");
    private final TmailBlobStoreVaultGarbageCollectionContext context;
    private final TmailBlobStoreDeletedMessageVault deletedMessageVault;
    private final ZonedDateTime beginningOfRetentionPeriod;

    public static class Factory {
        private final TmailBlobStoreDeletedMessageVault deletedMessageVault;

        @Inject
        public Factory(TmailBlobStoreDeletedMessageVault deletedMessageVault) {
            this.deletedMessageVault = deletedMessageVault;
        }

        public TmailBlobStoreVaultGarbageCollectionTask create() {
            return new TmailBlobStoreVaultGarbageCollectionTask(deletedMessageVault);
        }
    }

    private TmailBlobStoreVaultGarbageCollectionTask(TmailBlobStoreDeletedMessageVault deletedMessageVault) {
        this.beginningOfRetentionPeriod = deletedMessageVault.getBeginningOfRetentionPeriod();
        this.deletedMessageVault = deletedMessageVault;
        this.context = new TmailBlobStoreVaultGarbageCollectionContext();
    }

    @Override
    public Result run() {
        deletedMessageVault.deleteExpiredMessages(beginningOfRetentionPeriod, context)
            .block();

        return Result.COMPLETED;
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(
            beginningOfRetentionPeriod,
            context.getDeletedBuckets(),
            context.deletedBlobsCount(),
            Clock.systemUTC().instant()));
    }
}
