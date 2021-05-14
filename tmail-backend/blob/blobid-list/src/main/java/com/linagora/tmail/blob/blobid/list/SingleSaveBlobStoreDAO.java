package com.linagora.tmail.blob.blobid.list;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectStoreException;

import reactor.core.publisher.Mono;

public class SingleSaveBlobStoreDAO {
    private final BlobStoreDAO blobStoreDAO;
    private final BlobIdList blobIdList;
    private final BucketName defaultBucketName;

    @Inject
    public SingleSaveBlobStoreDAO(BlobStoreDAO blobStoreDAO,
                                  BlobIdList blobIdList,
                                  BucketName defaultBucketName) {
        this.blobStoreDAO = blobStoreDAO;
        this.blobIdList = blobIdList;
        this.defaultBucketName = defaultBucketName;
    }

    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        return Mono.from(blobIdList.isStored(blobId))
                .flatMap(isStored -> {
                    if (isStored.equals(true)) {
                        return Mono.empty();
                    }
                    return Mono.from(blobStoreDAO.save(bucketName, blobId, data))
                            .then(Mono.from(blobIdList.store(blobId)))
                            .then();
                });
    }

    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        if (defaultBucketName.equals(bucketName)) {
            return Mono.error(new ObjectStoreException("Can not delete in the default bucket when single save is enabled"));
        } else {
            return Mono.from(blobStoreDAO.delete(bucketName, blobId));
        }
    }
}

