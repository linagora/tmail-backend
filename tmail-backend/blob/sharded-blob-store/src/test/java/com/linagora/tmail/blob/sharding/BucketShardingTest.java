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

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.TestBlobId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BucketShardingTest {
    private static final BucketName BUCKET = BucketName.of("blobs");

    @AfterEach
    void tearDown() {
        System.clearProperty(BucketSharding.SHARD_COUNT_PROPERTY);
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
    void fromSystemPropertiesShouldThrowOnInvalidShardCount() {
        System.setProperty(BucketSharding.SHARD_COUNT_PROPERTY, "invalid");

        assertThatThrownBy(BucketSharding::fromSystemProperties)
            .isInstanceOf(IllegalArgumentException.class);
    }
}
