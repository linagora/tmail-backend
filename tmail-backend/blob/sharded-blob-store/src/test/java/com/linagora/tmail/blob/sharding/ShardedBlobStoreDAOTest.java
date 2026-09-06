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

import static org.apache.james.blob.api.BlobStoreDAOFixture.CUSTOM_BUCKET_NAME;
import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.MetadataAwareBlobStoreDAOContract;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ShardedBlobStoreDAOTest implements BlobStoreDAOContract, MetadataAwareBlobStoreDAOContract {
    private static final BucketSharding SHARDING = new BucketSharding(8);

    private MemoryBlobStoreDAO delegate;
    private ShardedBlobStoreDAO testee;

    @BeforeEach
    void setUp() {
        delegate = new MemoryBlobStoreDAO();
        testee = new ShardedBlobStoreDAO(delegate, SHARDING);
    }

    @Override
    public BlobStoreDAO testee() {
        return testee;
    }

    @Override
    @Disabled("Not supported by the underlying memory blob store")
    public void listBucketsShouldReturnBucketsWithNoBlob() {

    }

    @Test
    void saveShouldWriteToTheShardOwningTheBlobId() {
        BlobId blobId = new TestBlobId("blob-1");

        Mono.from(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block();

        assertThat(Flux.from(delegate.listBuckets()).collectList().block())
            .containsExactly(SHARDING.physicalBucket(TEST_BUCKET_NAME, blobId));
    }

    @Test
    void severalBlobsShouldLandInSeveralPhysicalBuckets() {
        List<BlobId> blobIds = someBlobIds(100);
        blobIds.forEach(blobId -> Mono.from(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block());

        assertThat(Flux.from(delegate.listBuckets()).collectList().block())
            .containsExactlyInAnyOrderElementsOf(SHARDING.physicalBuckets(TEST_BUCKET_NAME));
    }

    @Test
    void listBlobsShouldMergeEveryShard() {
        List<BlobId> blobIds = someBlobIds(100);
        blobIds.forEach(blobId -> Mono.from(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block());

        assertThat(Flux.from(testee.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .containsExactlyInAnyOrderElementsOf(blobIds);
    }

    @Test
    void listBlobsWithPrefixShouldMergeEveryShard() {
        List<BlobId> recoveryBlobIds = IntStream.range(0, 100)
            .mapToObj(i -> (BlobId) new TestBlobId(BlobStoreDAO.RECOVERY_BLOB_PREFIX + i))
            .collect(ImmutableList.toImmutableList());
        recoveryBlobIds.forEach(blobId -> Mono.from(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block());
        someBlobIds(100).forEach(blobId -> Mono.from(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block());

        assertThat(Flux.from(testee.listBlobs(TEST_BUCKET_NAME, BlobStoreDAO.RECOVERY_BLOB_PREFIX)).collectList().block())
            .containsExactlyInAnyOrderElementsOf(recoveryBlobIds);
    }

    @Test
    void deleteBucketShouldDeleteEveryShard() {
        someBlobIds(100).forEach(blobId -> Mono.from(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block());

        Mono.from(testee.deleteBucket(TEST_BUCKET_NAME)).block();

        assertThat(Flux.from(delegate.listBuckets()).collectList().block())
            .isEmpty();
    }

    @Test
    void deleteSeveralBlobsShouldSpanOverShards() {
        List<BlobId> blobIds = someBlobIds(100);
        blobIds.forEach(blobId -> Mono.from(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block());

        Mono.from(testee.delete(TEST_BUCKET_NAME, blobIds)).block();

        assertThat(Flux.from(testee.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    void omittedBucketShouldBeStoredUnderItsPlainName() {
        ShardedBlobStoreDAO omittingTestBucket = new ShardedBlobStoreDAO(delegate,
            new BucketSharding(SHARDING.shardCount(), ImmutableSet.of(TEST_BUCKET_NAME)));
        List<BlobId> blobIds = someBlobIds(100);

        blobIds.forEach(blobId -> Mono.from(omittingTestBucket.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block());

        assertThat(Flux.from(delegate.listBuckets()).collectList().block())
            .containsExactly(TEST_BUCKET_NAME);
        assertThat(Flux.from(omittingTestBucket.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .containsExactlyInAnyOrderElementsOf(blobIds);
        assertThat(Flux.from(omittingTestBucket.listBuckets()).collectList().block())
            .containsExactly(TEST_BUCKET_NAME);
    }

    @Test
    void omittedBucketShouldNotAffectTheShardedOnes() {
        ShardedBlobStoreDAO omittingCustomBucket = new ShardedBlobStoreDAO(delegate,
            new BucketSharding(SHARDING.shardCount(), ImmutableSet.of(CUSTOM_BUCKET_NAME)));
        List<BlobId> blobIds = someBlobIds(100);

        blobIds.forEach(blobId -> {
            Mono.from(omittingCustomBucket.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block();
            Mono.from(omittingCustomBucket.save(CUSTOM_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block();
        });

        assertThat(Flux.from(delegate.listBuckets()).collectList().block())
            .containsExactlyInAnyOrderElementsOf(ImmutableList.<BucketName>builder()
                .addAll(SHARDING.physicalBuckets(TEST_BUCKET_NAME))
                .add(CUSTOM_BUCKET_NAME)
                .build());
        assertThat(Flux.from(omittingCustomBucket.listBuckets()).collectList().block())
            .containsExactlyInAnyOrder(TEST_BUCKET_NAME, CUSTOM_BUCKET_NAME);
    }

    @Test
    void listBucketsShouldIgnoreBucketsNotBelongingToTheShardingLayout() {
        BlobId blobId = new TestBlobId("blob-1");
        Mono.from(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block();
        Mono.from(delegate.save(BucketName.of("legacy-unsharded"), blobId, SHORT_BYTEARRAY)).block();

        assertThat(Flux.from(testee.listBuckets()).collectList().block())
            .containsExactly(TEST_BUCKET_NAME);
    }

    private List<BlobId> someBlobIds(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> (BlobId) new TestBlobId("blob-" + i))
            .collect(ImmutableList.toImmutableList());
    }
}
