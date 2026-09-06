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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.TestBlobId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class BucketShardingTest {
    private static final BucketName BUCKET = BucketName.of("blobs");

    /** The four logical buckets a distributed Twake Mail deployment writes to. */
    private static final BucketName UPLOADS_BUCKET = BucketName.of("jmap-uploads");
    private static final BucketName VAULT_BUCKET = BucketName.of("tmail-deleted-message-vault");
    private static final BucketName MAIL_PROCESSING_BUCKET = BucketName.of("mail-processing");
    private static final Set<BucketName> TMAIL_BUCKETS =
        ImmutableSet.of(BUCKET, UPLOADS_BUCKET, VAULT_BUCKET, MAIL_PROCESSING_BUCKET);

    @AfterEach
    void tearDown() {
        System.clearProperty(BucketSharding.SHARD_COUNT_PROPERTY);
        System.clearProperty(BucketSharding.OMITTED_BUCKETS_PROPERTY);
    }

    @Test
    void physicalBucketShouldZeroPadTheShardNumber() {
        BucketSharding sharding = new BucketSharding(256);

        assertThat(sharding.physicalBuckets(BUCKET))
            .startsWith(BucketName.of("blobs-000"), BucketName.of("blobs-001"))
            .endsWith(BucketName.of("blobs-255"))
            .hasSize(256);
    }

    @Test
    void physicalBucketShouldNotPadBeyondTheShardCountWidth() {
        BucketSharding sharding = new BucketSharding(10);

        assertThat(sharding.physicalBuckets(BUCKET))
            .containsExactly(BucketName.of("blobs-0"), BucketName.of("blobs-1"), BucketName.of("blobs-2"),
                BucketName.of("blobs-3"), BucketName.of("blobs-4"), BucketName.of("blobs-5"),
                BucketName.of("blobs-6"), BucketName.of("blobs-7"), BucketName.of("blobs-8"),
                BucketName.of("blobs-9"));
    }

    @Test
    void logicalBucketShouldReverPhysicalBucket() {
        BucketSharding sharding = new BucketSharding(256);

        assertThat(sharding.physicalBuckets(BUCKET)
            .stream()
            .map(sharding::logicalBucket)
            .distinct())
            .containsExactly(Optional.of(BUCKET));
    }

    @Test
    void logicalBucketShouldRejectBucketsOutOfTheLayout() {
        BucketSharding sharding = new BucketSharding(256);

        assertThat(sharding.logicalBucket(BucketName.of("blobs"))).isEmpty();
        assertThat(sharding.logicalBucket(BucketName.of("blobs-1"))).isEmpty();
        assertThat(sharding.logicalBucket(BucketName.of("blobs-abc"))).isEmpty();
        assertThat(sharding.logicalBucket(BucketName.of("blobs-999"))).isEmpty();
        assertThat(sharding.logicalBucket(BucketName.of("-000"))).isEmpty();
    }

    @Test
    void shardOfShouldBeStableAcrossCalls() {
        BucketSharding sharding = new BucketSharding(256);
        TestBlobId blobId = new TestBlobId("2b8b46e9-1a1e-4a4a-bb0a-3f4a3f37a1e0");

        assertThat(sharding.shardOf(blobId)).isEqualTo(sharding.shardOf(blobId));
    }

    @Test
    void shardOfShouldSpreadBlobIdsEvenly() {
        int shardCount = 256;
        int blobCount = 256 * 400;
        BucketSharding sharding = new BucketSharding(shardCount);

        Map<Integer, Long> perShard = IntStream.range(0, blobCount)
            .mapToObj(i -> new TestBlobId("d6f0c1a5b2e34" + i))
            .collect(Collectors.groupingBy(sharding::shardOf, Collectors.counting()));

        assertThat(perShard).hasSize(shardCount);
        assertThat(perShard.values()).allSatisfy(count -> assertThat(count).isBetween(300L, 500L));
    }

    @Test
    void shardCountShouldBeStrictlyPositive() {
        assertThatThrownBy(() -> new BucketSharding(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BucketSharding(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new BucketSharding(1)).doesNotThrowAnyException();
    }

    @Test
    void fromSystemPropertiesShouldBeEmptyWhenPropertyIsOmitted() {
        assertThat(BucketSharding.fromSystemProperties()).isEmpty();
    }

    @Test
    void fromSystemPropertiesShouldBeEmptyWhenPropertyIsBlank() {
        System.setProperty(BucketSharding.SHARD_COUNT_PROPERTY, "  ");

        assertThat(BucketSharding.fromSystemProperties()).isEmpty();
    }

    @Test
    void fromSystemPropertiesShouldReadTheShardCount() {
        System.setProperty(BucketSharding.SHARD_COUNT_PROPERTY, "256");

        assertThat(BucketSharding.fromSystemProperties()).contains(new BucketSharding(256));
    }

    @Test
    void omittedBucketShouldNotBeSharded() {
        BucketSharding sharding = new BucketSharding(256, ImmutableSet.of(BUCKET));

        assertThat(sharding.physicalBuckets(BUCKET)).containsExactly(BUCKET);
        assertThat(sharding.physicalBucket(BUCKET, new TestBlobId("blob-1"))).isEqualTo(BUCKET);
    }

    @Test
    void omittedBucketShouldBeItsOwnLogicalBucket() {
        BucketSharding sharding = new BucketSharding(256, ImmutableSet.of(BUCKET));

        assertThat(sharding.logicalBucket(BUCKET)).contains(BUCKET);
    }

    @Test
    void omittingABucketShouldNotAffectTheOthers() {
        BucketSharding sharding = new BucketSharding(4, ImmutableSet.of(BucketName.of("jmap-uploads")));

        assertThat(sharding.physicalBuckets(BUCKET))
            .containsExactly(BucketName.of("blobs-0"), BucketName.of("blobs-1"),
                BucketName.of("blobs-2"), BucketName.of("blobs-3"));
    }

    @Test
    void fromSystemPropertiesShouldDefaultToNoOmittedBucket() {
        System.setProperty(BucketSharding.SHARD_COUNT_PROPERTY, "256");

        assertThat(BucketSharding.fromSystemProperties()).contains(new BucketSharding(256, ImmutableSet.of()));
    }

    @Test
    void fromSystemPropertiesShouldReadOmittedBuckets() {
        System.setProperty(BucketSharding.SHARD_COUNT_PROPERTY, "256");
        System.setProperty(BucketSharding.OMITTED_BUCKETS_PROPERTY, "jmap-uploads, mail-processing");

        assertThat(BucketSharding.fromSystemProperties())
            .contains(new BucketSharding(256, ImmutableSet.of(
                BucketName.of("jmap-uploads"), BucketName.of("mail-processing"))));
    }

    @Test
    void fromSystemPropertiesShouldIgnoreOmittedBucketsWhenShardingIsOff() {
        System.setProperty(BucketSharding.OMITTED_BUCKETS_PROPERTY, "jmap-uploads");

        assertThat(BucketSharding.fromSystemProperties()).isEmpty();
    }

    /**
     * Worked example over the four logical buckets of a Twake Mail deployment, sharded four ways, with the two
     * buckets that stay small enough to be listed as they are left out.
     *
     * <p>The object storage connector then resolves those into actual bucket names, which for S3 also means
     * prepending {@code objectstorage.bucketPrefix} - see {@code DistributedBlobStoreBucketShardingTest} in the
     * distributed app for the end to end picture.</p>
     */
    @Test
    void fourShardsOverTmailBucketsShouldYieldTheDocumentedLayout() {
        BucketSharding sharding = new BucketSharding(4, ImmutableSet.of(UPLOADS_BUCKET, MAIL_PROCESSING_BUCKET));

        assertThat(TMAIL_BUCKETS.stream()
            .collect(Collectors.toMap(BucketName::asString, sharding::physicalBuckets)))
            .containsExactlyInAnyOrderEntriesOf(Map.of(
                "blobs", List.of(BucketName.of("blobs-0"), BucketName.of("blobs-1"),
                    BucketName.of("blobs-2"), BucketName.of("blobs-3")),
                "tmail-deleted-message-vault", List.of(BucketName.of("tmail-deleted-message-vault-0"),
                    BucketName.of("tmail-deleted-message-vault-1"), BucketName.of("tmail-deleted-message-vault-2"),
                    BucketName.of("tmail-deleted-message-vault-3")),
                "jmap-uploads", List.of(UPLOADS_BUCKET),
                "mail-processing", List.of(MAIL_PROCESSING_BUCKET)));
    }

    @Test
    void everyTmailBucketShouldBeReadBackFromItsShards() {
        BucketSharding sharding = new BucketSharding(4, ImmutableSet.of(UPLOADS_BUCKET, MAIL_PROCESSING_BUCKET));

        assertThat(TMAIL_BUCKETS.stream()
            .flatMap(logicalBucket -> sharding.physicalBuckets(logicalBucket).stream())
            .map(sharding::logicalBucket)
            .flatMap(Optional::stream)
            .collect(ImmutableSet.toImmutableSet()))
            .containsExactlyInAnyOrderElementsOf(TMAIL_BUCKETS);
    }

    /**
     * Guards the sample size used by {@code DistributedBlobStoreBucketShardingTest}: sixteen blobs are enough for
     * every one of the four shards of every one of those buckets to be actually created.
     */
    @Test
    void sixteenBlobsShouldReachEveryShardOfEveryTmailBucket() {
        BucketSharding sharding = new BucketSharding(4);

        TMAIL_BUCKETS.forEach(logicalBucket -> assertThat(IntStream.range(0, 16)
            .mapToObj(i -> new TestBlobId(logicalBucket.asString() + "-blob-" + i))
            .map(blobId -> sharding.physicalBucket(logicalBucket, blobId))
            .collect(ImmutableSet.toImmutableSet()))
            .containsExactlyInAnyOrderElementsOf(sharding.physicalBuckets(logicalBucket)));
    }

    @Test
    void fromSystemPropertiesShouldThrowOnInvalidShardCount() {
        System.setProperty(BucketSharding.SHARD_COUNT_PROPERTY, "invalid");

        assertThatThrownBy(BucketSharding::fromSystemProperties)
            .isInstanceOf(IllegalArgumentException.class);
    }
}
