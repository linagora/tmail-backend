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

package com.linagora.tmail.blob.sharding;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spreads the blobs of a logical bucket over {@link BucketSharding#shardCount()} physical buckets, so that no
 * single Ceph / Rados gateway bucket index grows past the point where an ordered listing stops being affordable.
 *
 * <p>Reads, writes and single deletes are routed to the one bucket owning the blobId. Listings and bucket deletions
 * fan out over every shard.</p>
 */
public class ShardedBlobStoreDAO implements BlobStoreDAO {
    /**
     * Listing a shard already pages through a whole bucket index: fanning out aggressively would multiply the
     * pressure put on the Rados gateway by as many shards.
     */
    private static final int LISTING_CONCURRENCY = 2;
    private static final int DELETION_CONCURRENCY = 8;

    private final BlobStoreDAO delegate;
    private final BucketSharding sharding;

    public ShardedBlobStoreDAO(BlobStoreDAO delegate, BucketSharding sharding) {
        this.delegate = delegate;
        this.sharding = sharding;
    }

    @Override
    public InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return delegate.read(shard(bucketName, blobId), blobId);
    }

    @Override
    public Mono<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.from(delegate.readReactive(shard(bucketName, blobId), blobId));
    }

    @Override
    public Mono<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.from(delegate.readBytes(shard(bucketName, blobId), blobId));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
        return Mono.from(delegate.save(shard(bucketName, blobId), blobId, blob));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.from(delegate.delete(shard(bucketName, blobId), blobId));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        Map<BucketName, ImmutableList<BlobId>> perShard = blobIds.stream()
            .collect(Collectors.groupingBy(blobId -> shard(bucketName, blobId),
                ImmutableList.toImmutableList()));

        return Flux.fromIterable(perShard.entrySet())
            .flatMap(entry -> delegate.delete(entry.getKey(), entry.getValue()), DELETION_CONCURRENCY)
            .then();
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return Flux.fromIterable(sharding.physicalBuckets(bucketName))
            .flatMap(delegate::deleteBucket, DELETION_CONCURRENCY)
            .then();
    }

    @Override
    public Flux<BucketName> listBuckets() {
        return Flux.from(delegate.listBuckets())
            .flatMap(physicalBucket -> Mono.justOrEmpty(sharding.logicalBucket(physicalBucket)))
            .distinct();
    }

    @Override
    public Flux<BlobId> listBlobs(BucketName bucketName) {
        return Flux.fromIterable(sharding.physicalBuckets(bucketName))
            .flatMap(delegate::listBlobs, LISTING_CONCURRENCY);
    }

    @Override
    public Flux<BlobId> listBlobs(BucketName bucketName, String prefix) {
        return Flux.fromIterable(sharding.physicalBuckets(bucketName))
            .flatMap(physicalBucket -> delegate.listBlobs(physicalBucket, prefix), LISTING_CONCURRENCY);
    }

    private BucketName shard(BucketName bucketName, BlobId blobId) {
        return sharding.physicalBucket(bucketName, blobId);
    }
}
