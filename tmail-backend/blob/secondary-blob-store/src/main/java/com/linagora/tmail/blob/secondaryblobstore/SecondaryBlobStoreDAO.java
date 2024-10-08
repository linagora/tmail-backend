package com.linagora.tmail.blob.secondaryblobstore;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;

public class SecondaryBlobStoreDAO implements BlobStoreDAO {
    record SavingStatus(Optional<Throwable> e) {
        public static SavingStatus success() {
            return new SavingStatus(Optional.empty());
        }

        public static SavingStatus failure(Throwable e) {
            return new SavingStatus(Optional.of(e));
        }

        public boolean isSuccess() {
            return e.isEmpty();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SecondaryBlobStoreDAO.class);

    private final BlobStoreDAO firstBlobStoreDAO;
    private final BlobStoreDAO secondBlobStoreDAO;

    public SecondaryBlobStoreDAO(BlobStoreDAO firstBlobStoreDAO, BlobStoreDAO secondBlobStoreDAO) {
        this.firstBlobStoreDAO = firstBlobStoreDAO;
        this.secondBlobStoreDAO = secondBlobStoreDAO;
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        try {
            return firstBlobStoreDAO.read(bucketName, blobId);
        } catch (ObjectStoreException ex) {
            LOGGER.warn("Fail to read from the first blob store with bucket name {} and blobId {}. Use second blob store", bucketName.asString(), blobId.asString(), ex);
            return secondBlobStoreDAO.read(bucketName, blobId);
        }
    }

    @Override
    public Mono<InputStream> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.from(firstBlobStoreDAO.readReactive(bucketName, blobId))
            .onErrorResume(ObjectStoreException.class, ex -> {
                LOGGER.warn("Fail to read from the first blob store with bucket name {} and blobId {}. Use second blob store", bucketName.asString(), blobId.asString(), ex);
                return Mono.from(secondBlobStoreDAO.readReactive(bucketName, blobId));
            });
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.from(firstBlobStoreDAO.readBytes(bucketName, blobId))
            .onErrorResume(ObjectStoreException.class, ex -> {
                LOGGER.warn("Fail to read from the first blob store with bucket name {} and blobId {}. Use second blob store", bucketName.asString(), blobId.asString(), ex);
                return Mono.from(secondBlobStoreDAO.readBytes(bucketName, blobId));
            });
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        return Flux.merge(asSavingStatus(firstBlobStoreDAO.save(bucketName, blobId, data)),
                asSavingStatus(secondBlobStoreDAO.save(bucketName, blobId, data)))
            .collectList()
            .handle(this::merge);
    }

    @Override
    // TODO Could be optimized with FileBackedOutputStream
    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        return Mono.fromCallable(() -> IOUtils.toByteArray(inputStream))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(data -> Flux.merge(asSavingStatus(firstBlobStoreDAO.save(bucketName, blobId, data)),
                    asSavingStatus(secondBlobStoreDAO.save(bucketName, blobId, data)))
                .collectList()
                .handle(this::merge));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Flux.merge(asSavingStatus(firstBlobStoreDAO.save(bucketName, blobId, content)),
                asSavingStatus(secondBlobStoreDAO.save(bucketName, blobId, content)))
            .collectList()
            .handle(this::merge);
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Flux.merge(asSavingStatus(firstBlobStoreDAO.delete(bucketName, blobId)),
                asSavingStatus(secondBlobStoreDAO.delete(bucketName, blobId)))
            .collectList()
            .handle(this::merge);
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return Flux.merge(asSavingStatus(firstBlobStoreDAO.delete(bucketName, blobIds)),
                asSavingStatus(secondBlobStoreDAO.delete(bucketName, blobIds)))
            .collectList()
            .handle(this::merge);
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return Flux.merge(asSavingStatus(firstBlobStoreDAO.deleteBucket(bucketName)),
                asSavingStatus(secondBlobStoreDAO.deleteBucket(bucketName)))
            .collectList()
            .handle(this::merge);
    }

    @Override
    public Flux<BucketName> listBuckets() {
        return Flux.from(firstBlobStoreDAO.listBuckets());
    }

    @Override
    public Flux<BlobId> listBlobs(BucketName bucketName) {
        return Flux.from(firstBlobStoreDAO.listBlobs(bucketName));
    }

    private Mono<SavingStatus> asSavingStatus(Publisher<Void> publisher) {
        return Mono.from(publisher).then(Mono.just(SavingStatus.success()))
            .onErrorResume(e -> Mono.just(SavingStatus.failure(e)));
    }

    private void merge(List<SavingStatus> savingStatuses, SynchronousSink<Void> sink) {
        Preconditions.checkArgument(savingStatuses.size() == 2);
        boolean bothSucceeded = savingStatuses.get(0).isSuccess() && savingStatuses.get(1).isSuccess();
        boolean bothFailed = !savingStatuses.get(0).isSuccess() && !savingStatuses.get(1).isSuccess();
        if (bothSucceeded) {
            sink.complete();
            return;
        }
        if (bothFailed) {
            sink.error(new ObjectStoreException("Failure to save in both blobStore. First exception was:", savingStatuses.getFirst().e.get()));
            return;
        }
        if (savingStatuses.get(0).isSuccess()) {
            LOGGER.warn("Failure to save in secondary blobStore", savingStatuses.get(1).e.get());
        } else {
            LOGGER.warn("Failure to save in primary blobStore", savingStatuses.get(0).e.get());
        }
        sink.complete();
    }
}
