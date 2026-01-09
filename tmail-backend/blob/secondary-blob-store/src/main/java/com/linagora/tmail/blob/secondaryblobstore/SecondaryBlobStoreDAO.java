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

package com.linagora.tmail.blob.secondaryblobstore;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.RegistrationKey;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class SecondaryBlobStoreDAO implements BlobStoreDAO {

    private static class FileBackedOutputStreamByteSource extends ByteSource {
        private final FileBackedOutputStream stream;
        private final long size;

        private FileBackedOutputStreamByteSource(FileBackedOutputStream stream, long size) {
            Preconditions.checkArgument(size >= 0, "'size' must be positive");
            this.stream = stream;
            this.size = size;
        }

        @Override
        public InputStream openStream() throws IOException {
            return stream.asByteSource().openStream();
        }

        @Override
        public com.google.common.base.Optional<Long> sizeIfKnown() {
            return com.google.common.base.Optional.of(size);
        }

        @Override
        public long size() {
            return size;
        }
    }

    record SavingStatus(Optional<Throwable> e, ObjectStorageIdentity objectStorageIdentity) {
        public static SavingStatus success(ObjectStorageIdentity objectStorageIdentity) {
            return new SavingStatus(Optional.empty(), objectStorageIdentity);
        }

        public static SavingStatus failure(Throwable e, ObjectStorageIdentity objectStorageIdentity) {
            return new SavingStatus(Optional.of(e), objectStorageIdentity);
        }

        public boolean isSuccess() {
            return e.isEmpty();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SecondaryBlobStoreDAO.class);
    private static final Set<RegistrationKey> NO_REGISTRATION_KEYS = ImmutableSet.of();
    private static final int FILE_THRESHOLD = 100 * 1024 * 1024;

    private final BlobStoreDAO primaryBlobStoreDAO;
    private final BlobStoreDAO secondaryBlobStoreDAO;
    private final String secondaryBucketSuffix;
    private final EventBus eventBus;

    public SecondaryBlobStoreDAO(BlobStoreDAO primaryBlobStoreDAO,
                                 BlobStoreDAO secondaryBlobStoreDAO,
                                 String secondaryBucketSuffix,
                                 EventBus eventBus) {
        this.primaryBlobStoreDAO = primaryBlobStoreDAO;
        this.secondaryBlobStoreDAO = secondaryBlobStoreDAO;
        this.secondaryBucketSuffix = secondaryBucketSuffix;
        this.eventBus = eventBus;
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        try {
            return primaryBlobStoreDAO.read(bucketName, blobId);
        } catch (Exception ex) {
            try {
                InputStream inputStream = secondaryBlobStoreDAO.read(withSuffix(bucketName), blobId);
                LOGGER.warn("Fail to read from the first blob store with bucket name {} and blobId {}. Use second blob store", bucketName.asString(), blobId.asString(), ex);
                return inputStream;
            } catch (Exception ex2) {
                if (ex instanceof ObjectNotFoundException && ex2 instanceof ObjectNotFoundException) {
                    throw ex;
                }
                throw new ObjectStoreException("Failure to read " + blobId.asString() + " in bucket " + bucketName.asString() + " on both blobstores, first error:", ex);
            }
        }
    }

    @Override
    public Mono<InputStream> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.from(primaryBlobStoreDAO.readReactive(bucketName, blobId))
            .onErrorResume(ex -> Mono.from(secondaryBlobStoreDAO.readReactive(withSuffix(bucketName), blobId))
                .onErrorResume(ex2 -> {
                    if (ex instanceof ObjectNotFoundException && ex2 instanceof ObjectNotFoundException) {
                        return Mono.error(ex);
                    }
                    return Mono.error(new ObjectStoreException("Failure to read " + blobId.asString() + " in bucket " + bucketName.asString() + " on both blobstores, first error:", ex));
                })
                .doOnSuccess(any -> LOGGER.warn("Fail to read from the first blob store with bucket name {} and blobId {}. Use second blob store", bucketName.asString(), blobId.asString(), ex)));
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.from(primaryBlobStoreDAO.readBytes(bucketName, blobId))
            .onErrorResume(ex -> Mono.from(secondaryBlobStoreDAO.readBytes(withSuffix(bucketName), blobId))
                .onErrorResume(ex2 -> {
                    if (ex instanceof ObjectNotFoundException && ex2 instanceof ObjectNotFoundException) {
                        return Mono.error(ex);
                    }
                    return Mono.error(new ObjectStoreException("Failure to read " + blobId.asString() + " in bucket " + bucketName.asString() + " on both blobstores, first error:", ex));
                })
                .doOnSuccess(any -> LOGGER.warn("Fail to read from the first blob store with bucket name {} and blobId {}. Use second blob store", bucketName.asString(), blobId.asString(), ex)));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        return Flux.merge(asSavingStatus(primaryBlobStoreDAO.save(bucketName, blobId, data), ObjectStorageIdentity.PRIMARY),
                asSavingStatus(secondaryBlobStoreDAO.save(withSuffix(bucketName), blobId, data), ObjectStorageIdentity.SECONDARY))
            .collectList()
            .flatMap(savingStatuses -> merge(blobId, savingStatuses,
                failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BlobAddition(Event.EventId.random(), bucketName, blobId, failedObjectStorage), NO_REGISTRATION_KEYS)));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        return Mono.using(
                () -> new FileBackedOutputStream(FILE_THRESHOLD),
                fileBackedOutputStream -> Mono.fromCallable(() -> IOUtils.copy(inputStream, fileBackedOutputStream))
                    .flatMap(size -> Flux.merge(
                            asSavingStatus(primaryBlobStoreDAO.save(bucketName, blobId, new FileBackedOutputStreamByteSource(fileBackedOutputStream, size)), ObjectStorageIdentity.PRIMARY),
                            asSavingStatus(secondaryBlobStoreDAO.save(withSuffix(bucketName), blobId, new FileBackedOutputStreamByteSource(fileBackedOutputStream, size)), ObjectStorageIdentity.SECONDARY))
                        .collectList()
                        .flatMap(savingStatuses -> merge(blobId, savingStatuses,
                            failedObjectStorage -> eventBus.dispatch(
                                new FailedBlobEvents.BlobAddition(Event.EventId.random(), bucketName, blobId, failedObjectStorage),
                                NO_REGISTRATION_KEYS)))),
                Throwing.consumer(FileBackedOutputStream::reset))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Flux.merge(asSavingStatus(primaryBlobStoreDAO.save(bucketName, blobId, content), ObjectStorageIdentity.PRIMARY),
                asSavingStatus(secondaryBlobStoreDAO.save(withSuffix(bucketName), blobId, content), ObjectStorageIdentity.SECONDARY))
            .collectList()
            .flatMap(savingStatuses -> merge(blobId, savingStatuses,
                failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BlobAddition(Event.EventId.random(), bucketName, blobId, failedObjectStorage), NO_REGISTRATION_KEYS)));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Flux.merge(asSavingStatus(primaryBlobStoreDAO.delete(bucketName, blobId), ObjectStorageIdentity.PRIMARY),
                asSavingStatus(secondaryBlobStoreDAO.delete(withSuffix(bucketName), blobId), ObjectStorageIdentity.SECONDARY))
            .collectList()
            .flatMap(savingStatuses -> merge(blobId, savingStatuses,
                failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BlobsDeletion(Event.EventId.random(), bucketName, ImmutableList.of(blobId), failedObjectStorage), NO_REGISTRATION_KEYS)));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return Flux.merge(asSavingStatus(primaryBlobStoreDAO.delete(bucketName, blobIds), ObjectStorageIdentity.PRIMARY),
                asSavingStatus(secondaryBlobStoreDAO.delete(withSuffix(bucketName), blobIds), ObjectStorageIdentity.SECONDARY))
            .collectList()
            .flatMap(savingStatuses -> merge(savingStatuses,
                failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BlobsDeletion(Event.EventId.random(), bucketName, blobIds, failedObjectStorage), NO_REGISTRATION_KEYS)));
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return Flux.merge(asSavingStatus(primaryBlobStoreDAO.deleteBucket(bucketName), ObjectStorageIdentity.PRIMARY),
                asSavingStatus(secondaryBlobStoreDAO.deleteBucket(withSuffix(bucketName)), ObjectStorageIdentity.SECONDARY))
            .collectList()
            .flatMap(savingStatuses -> merge(savingStatuses,
                failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BucketDeletion(Event.EventId.random(), bucketName, failedObjectStorage), NO_REGISTRATION_KEYS)));
    }

    @Override
    public Flux<BlobId> listBlobs(BucketName bucketName) {
        return Flux.from(primaryBlobStoreDAO.listBlobs(bucketName));
    }

    private Mono<SavingStatus> asSavingStatus(Publisher<Void> publisher, ObjectStorageIdentity objectStorageIdentity) {
        return Mono.from(publisher).then(Mono.just(SavingStatus.success(objectStorageIdentity)))
            .onErrorResume(e -> Mono.just(SavingStatus.failure(e, objectStorageIdentity)));
    }

    private Mono<Void> merge(List<SavingStatus> savingStatuses, Function<ObjectStorageIdentity, Mono<Void>> partialFailureHandler) {
        Preconditions.checkArgument(savingStatuses.size() == 2);
        boolean bothSucceeded = savingStatuses.get(0).isSuccess() && savingStatuses.get(1).isSuccess();
        boolean bothFailed = !savingStatuses.get(0).isSuccess() && !savingStatuses.get(1).isSuccess();
        if (bothSucceeded) {
            return Mono.empty();
        }
        if (bothFailed) {
            return Mono.error(new ObjectStoreException("Failure to save in both blobStore. First exception was:", savingStatuses.getFirst().e.get()));
        }

        SavingStatus failedSavingStatus = savingStatuses.stream().filter(savingStatus -> !savingStatus.isSuccess()).findFirst().get();
        LOGGER.warn("Failure to save in {} blobStore", failedSavingStatus.objectStorageIdentity().name().toLowerCase(), failedSavingStatus.e.get());
        return partialFailureHandler.apply(failedSavingStatus.objectStorageIdentity());
    }

    private Mono<Void> merge(BlobId blobId, List<SavingStatus> savingStatuses, Function<ObjectStorageIdentity, Mono<Void>> partialFailureHandler) {
        Preconditions.checkArgument(savingStatuses.size() == 2);
        boolean bothSucceeded = savingStatuses.get(0).isSuccess() && savingStatuses.get(1).isSuccess();
        boolean bothFailed = !savingStatuses.get(0).isSuccess() && !savingStatuses.get(1).isSuccess();
        if (bothSucceeded) {
            return Mono.empty();
        }
        if (bothFailed) {
            return Mono.error(new ObjectStoreException("Failure to save " + blobId.asString() + " in both blobStore. First exception was:",
                savingStatuses.getFirst().e.get()));
        }

        SavingStatus failedSavingStatus = savingStatuses.stream().filter(savingStatus -> !savingStatus.isSuccess()).findFirst().get();
        LOGGER.warn("Failure to save {} in {} blobStore", blobId.asString(),
            failedSavingStatus.objectStorageIdentity().name().toLowerCase(), failedSavingStatus.e.get());
        return partialFailureHandler.apply(failedSavingStatus.objectStorageIdentity());
    }

    private BucketName withSuffix(BucketName bucketName) {
        return BucketName.of(bucketName.asString() + secondaryBucketSuffix);
    }

    @VisibleForTesting
    public BlobStoreDAO getFirstBlobStoreDAO() {
        return primaryBlobStoreDAO;
    }

    @VisibleForTesting
    public BlobStoreDAO getSecondBlobStoreDAO() {
        return secondaryBlobStoreDAO;
    }
}
