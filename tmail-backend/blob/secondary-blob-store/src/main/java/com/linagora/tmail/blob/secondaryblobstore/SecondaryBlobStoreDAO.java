package com.linagora.tmail.blob.secondaryblobstore;

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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class SecondaryBlobStoreDAO implements BlobStoreDAO {
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

    private final BlobStoreDAO primaryBlobStoreDAO;
    private final BlobStoreDAO secondaryBlobStoreDAO;
    private final EventBus eventBus;

    public SecondaryBlobStoreDAO(BlobStoreDAO primaryBlobStoreDAO,
                                 BlobStoreDAO secondaryBlobStoreDAO,
                                 EventBus eventBus) {
        this.primaryBlobStoreDAO = primaryBlobStoreDAO;
        this.secondaryBlobStoreDAO = secondaryBlobStoreDAO;
        this.eventBus = eventBus;
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        try {
            return primaryBlobStoreDAO.read(bucketName, blobId);
        } catch (Exception ex) {
            LOGGER.warn("Fail to read from the first blob store with bucket name {} and blobId {}. Use second blob store", bucketName.asString(), blobId.asString(), ex);
            return secondaryBlobStoreDAO.read(bucketName, blobId);
        }
    }

    @Override
    public Mono<InputStream> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.from(primaryBlobStoreDAO.readReactive(bucketName, blobId))
            .onErrorResume(ex -> {
                LOGGER.warn("Fail to read from the first blob store with bucket name {} and blobId {}. Use second blob store", bucketName.asString(), blobId.asString(), ex);
                return Mono.from(secondaryBlobStoreDAO.readReactive(bucketName, blobId));
            });
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.from(primaryBlobStoreDAO.readBytes(bucketName, blobId))
            .onErrorResume(ex -> {
                LOGGER.warn("Fail to read from the first blob store with bucket name {} and blobId {}. Use second blob store", bucketName.asString(), blobId.asString(), ex);
                return Mono.from(secondaryBlobStoreDAO.readBytes(bucketName, blobId));
            });
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        return Flux.merge(asSavingStatus(primaryBlobStoreDAO.save(bucketName, blobId, data), ObjectStorageIdentity.PRIMARY),
                asSavingStatus(secondaryBlobStoreDAO.save(bucketName, blobId, data), ObjectStorageIdentity.SECONDARY))
            .collectList()
            .flatMap(savingStatuses -> merge(savingStatuses,
                failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BlobAddition(Event.EventId.random(), bucketName, blobId, failedObjectStorage), NO_REGISTRATION_KEYS)));
    }

    @Override
    // TODO Could be optimized with FileBackedOutputStream
    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        return Mono.fromCallable(() -> IOUtils.toByteArray(inputStream))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(data -> Flux.merge(asSavingStatus(primaryBlobStoreDAO.save(bucketName, blobId, data), ObjectStorageIdentity.PRIMARY),
                    asSavingStatus(secondaryBlobStoreDAO.save(bucketName, blobId, data), ObjectStorageIdentity.SECONDARY))
                .collectList()
                .flatMap(savingStatuses -> merge(savingStatuses,
                    failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BlobAddition(Event.EventId.random(), bucketName, blobId, failedObjectStorage), NO_REGISTRATION_KEYS))));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Flux.merge(asSavingStatus(primaryBlobStoreDAO.save(bucketName, blobId, content), ObjectStorageIdentity.PRIMARY),
                asSavingStatus(secondaryBlobStoreDAO.save(bucketName, blobId, content), ObjectStorageIdentity.SECONDARY))
            .collectList()
            .flatMap(savingStatuses -> merge(savingStatuses,
                failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BlobAddition(Event.EventId.random(), bucketName, blobId, failedObjectStorage), NO_REGISTRATION_KEYS)));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Flux.merge(asSavingStatus(primaryBlobStoreDAO.delete(bucketName, blobId), ObjectStorageIdentity.PRIMARY),
                asSavingStatus(secondaryBlobStoreDAO.delete(bucketName, blobId), ObjectStorageIdentity.SECONDARY))
            .collectList()
            .flatMap(savingStatuses -> merge(savingStatuses,
                failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BlobsDeletion(Event.EventId.random(), bucketName, ImmutableList.of(blobId), failedObjectStorage), NO_REGISTRATION_KEYS)));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return Flux.merge(asSavingStatus(primaryBlobStoreDAO.delete(bucketName, blobIds), ObjectStorageIdentity.PRIMARY),
                asSavingStatus(secondaryBlobStoreDAO.delete(bucketName, blobIds), ObjectStorageIdentity.SECONDARY))
            .collectList()
            .flatMap(savingStatuses -> merge(savingStatuses,
                failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BlobsDeletion(Event.EventId.random(), bucketName, blobIds, failedObjectStorage), NO_REGISTRATION_KEYS)));
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return Flux.merge(asSavingStatus(primaryBlobStoreDAO.deleteBucket(bucketName), ObjectStorageIdentity.PRIMARY),
                asSavingStatus(secondaryBlobStoreDAO.deleteBucket(bucketName), ObjectStorageIdentity.SECONDARY))
            .collectList()
            .flatMap(savingStatuses -> merge(savingStatuses,
                failedObjectStorage -> eventBus.dispatch(new FailedBlobEvents.BucketDeletion(Event.EventId.random(), bucketName, failedObjectStorage), NO_REGISTRATION_KEYS)));
    }

    @Override
    public Flux<BucketName> listBuckets() {
        return Flux.from(primaryBlobStoreDAO.listBuckets());
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

    @VisibleForTesting
    public BlobStoreDAO getFirstBlobStoreDAO() {
        return primaryBlobStoreDAO;
    }

    @VisibleForTesting
    public BlobStoreDAO getSecondBlobStoreDAO() {
        return secondaryBlobStoreDAO;
    }
}
