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

package com.linagora.tmail.blob.blobid.list;

import java.util.Collection;
import java.util.function.Predicate;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class SingleSaveBlobStoreDAO implements BlobStoreDAO {
    private final BlobStoreDAO blobStoreDAO;
    private final BlobIdList blobIdList;
    private final BucketName defaultBucketName;
    private final Predicate<BlobId> isHeaderBlob;

    public SingleSaveBlobStoreDAO(BlobStoreDAO blobStoreDAO,
                                  BlobIdList blobIdList,
                                  BucketName defaultBucketName,
                                  BlobId.Factory blobIdFactory) {
        this.blobStoreDAO = blobStoreDAO;
        this.blobIdList = blobIdList;
        this.defaultBucketName = defaultBucketName;
        this.isHeaderBlob = new HeaderBlobIdPredicate(blobIdFactory);
    }

    @Override
    public InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return blobStoreDAO.read(bucketName, blobId);
    }

    @Override
    public Publisher<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
        return blobStoreDAO.readReactive(bucketName, blobId);
    }

    @Override
    public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
        return blobStoreDAO.readBytes(bucketName, blobId);
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
        if (isDeduplicated(bucketName, blobId)) {
            return Mono.from(blobIdList.isStored(blobId))
                .flatMap(isStored -> {
                    if (isStored) {
                        return Mono.empty();
                    }
                    return Mono.from(blobStoreDAO.save(bucketName, blobId, blob))
                        .then(Mono.from(blobIdList.store(blobId)))
                        .then();
                });
        } else {
            return Mono.from(blobStoreDAO.save(bucketName, blobId, blob));
        }
    }

    /**
     * Header blobs, unique by construction, are left out of the list: see {@link HeaderBlobIdPredicate}.
     */
    private boolean isDeduplicated(BucketName bucketName, BlobId blobId) {
        return defaultBucketName.equals(bucketName) && !isHeaderBlob.test(blobId);
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.from(blobStoreDAO.delete(bucketName, blobId));
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return Mono.from(blobStoreDAO.delete(bucketName, blobIds));
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        if (defaultBucketName.equals(bucketName)) {
            return Mono.error(new ObjectStoreException("Can not delete the default bucket when single save is enabled"));
        } else {
            return blobStoreDAO.deleteBucket(bucketName);
        }
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return blobStoreDAO.listBuckets();
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        return blobStoreDAO.listBlobs(bucketName);
    }
}

