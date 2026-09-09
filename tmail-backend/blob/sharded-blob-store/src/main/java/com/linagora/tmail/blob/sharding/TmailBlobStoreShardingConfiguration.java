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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * How a James logical {@link BucketName} is spread over a fixed set of physical buckets, the shard being derived from
 * the {@link BlobId}.
 *
 * <p>S3 compatibility is an API contract, not a behaviour contract: a Ceph / Rados gateway bucket is backed by
 * sharded omap indexes that should not hold much more than 100K entries each. Holding billions of objects in a
 * single bucket would require tens of thousands of index shards, and an <strong>ordered</strong> S3 listing would
 * then need to merge all of them, which does not hold. Splitting the objects across several buckets keeps each
 * bucket index within a listable size.</p>
 *
 * <p>Physical buckets are named {@code <logical bucket>-<zero padded shard number>}, eg. {@code default-000} up to
 * {@code default-255} for 256 shards. Because blobIds are content addressed, deduplication and garbage collection
 * still hold within a shard.</p>
 *
 * <p>Read from <code>blob.properties</code>:</p>
 *
 * <pre>
 * tmail.blobstore.shards=256
 * tmail.blobstore.shards.ommited.buckets=jmap-uploads,mail-processing
 * </pre>
 *
 * <p><strong>Both are written in stone</strong>: changing the shard count, or moving a bucket in or out of the
 * omitted list, relocates blobs onto another bucket and makes the already written ones unreachable.</p>
 *
 * @param shardCount        how many physical buckets a logical bucket is spread over, {@link #NO_SHARDING} to store
 *                          every bucket under its plain name
 * @param omittedBuckets    logical buckets left out of the layout, small enough to be listed as they are, and thus
 *                          stored under their plain name
 */
public record TmailBlobStoreShardingConfiguration(int shardCount, Set<BucketName> omittedBuckets) {
    public static final int NO_SHARDING = 0;
    public static final TmailBlobStoreShardingConfiguration DISABLED =
        new TmailBlobStoreShardingConfiguration(NO_SHARDING, ImmutableSet.of());

    static final String SHARD_COUNT_PROPERTY = "tmail.blobstore.shards";
    static final String OMITTED_BUCKETS_PROPERTY = "tmail.blobstore.shards.ommited.buckets";

    public static final char SEPARATOR = '-';

    /**
     * Murmur3 is stable across JVMs and Guava releases, which {@link String#hashCode()} guarantees too but with a
     * far poorer distribution over hexadecimal blobIds.
     */
    private static final HashFunction HASH_FUNCTION = Hashing.murmur3_32_fixed();

    public static TmailBlobStoreShardingConfiguration from(Configuration configuration) {
        int shardCount = Optional.ofNullable(configuration.getString(SHARD_COUNT_PROPERTY, null))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(TmailBlobStoreShardingConfiguration::parseShardCount)
            .orElse(NO_SHARDING);

        if (shardCount == NO_SHARDING) {
            return DISABLED;
        }
        return new TmailBlobStoreShardingConfiguration(shardCount,
            omittedBuckets(configuration.getStringArray(OMITTED_BUCKETS_PROPERTY)));
    }

    private static int parseShardCount(String shardCount) {
        try {
            return Integer.parseInt(shardCount);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(SHARD_COUNT_PROPERTY + " must be a positive integer, got " + shardCount, e);
        }
    }

    /**
     * James reads {@code blob.properties} with a comma list delimiter, so the property already reaches us split.
     * Values are trimmed nonetheless, blank ones dropped, so that {@code a, b} and {@code a,b,} both work.
     */
    private static Set<BucketName> omittedBuckets(String... omittedBuckets) {
        return Stream.of(omittedBuckets)
            .map(String::trim)
            .filter(bucketName -> !bucketName.isEmpty())
            .map(BucketName::of)
            .collect(ImmutableSet.toImmutableSet());
    }

    public static TmailBlobStoreShardingConfiguration of(int shardCount) {
        return new TmailBlobStoreShardingConfiguration(shardCount, ImmutableSet.of());
    }

    public static TmailBlobStoreShardingConfiguration of(int shardCount, BucketName... omittedBuckets) {
        return new TmailBlobStoreShardingConfiguration(shardCount, ImmutableSet.copyOf(omittedBuckets));
    }

    public TmailBlobStoreShardingConfiguration {
        Preconditions.checkArgument(shardCount >= NO_SHARDING, "%s cannot be negative", SHARD_COUNT_PROPERTY);
        omittedBuckets = ImmutableSet.copyOf(omittedBuckets);
    }

    public boolean enabled() {
        return shardCount > NO_SHARDING;
    }

    /**
     * Adapts omitted bucket names to a delegate receiving suffixed logical bucket names.
     *
     * <p>The secondary blob store appends its suffix before invoking its delegate, while omitted buckets are
     * configured using their original logical names. Without applying the same suffix here, an omitted bucket such
     * as {@code jmap-uploads} would be received as {@code jmap-uploads-copy}, fail the omission check, and be
     * unexpectedly sharded on the secondary store.</p>
     */
    public TmailBlobStoreShardingConfiguration forBucketSuffix(String bucketSuffix) {
        Preconditions.checkNotNull(bucketSuffix, "bucketSuffix");
        if (bucketSuffix.isEmpty()) {
            return this;
        }
        return new TmailBlobStoreShardingConfiguration(shardCount,
            omittedBuckets.stream()
                .map(bucket -> BucketName.of(bucket.asString() + bucketSuffix))
                .collect(ImmutableSet.toImmutableSet()));
    }

    /**
     * @return whether this bucket is left out of the layout, and thus stored under its plain name.
     */
    public boolean isOmitted(BucketName logicalBucket) {
        return omittedBuckets.contains(logicalBucket);
    }

    public int shardOf(BlobId blobId) {
        Preconditions.checkState(enabled(), "Cannot compute a shard when sharding is disabled");
        return Math.floorMod(HASH_FUNCTION.hashString(blobId.asString(), StandardCharsets.UTF_8).asInt(), shardCount);
    }

    public BucketName physicalBucket(BucketName logicalBucket, BlobId blobId) {
        if (!enabled() || isOmitted(logicalBucket)) {
            return logicalBucket;
        }
        return physicalBucket(logicalBucket, shardOf(blobId));
    }

    public BucketName physicalBucket(BucketName logicalBucket, int shard) {
        if (!enabled()) {
            return logicalBucket;
        }
        Preconditions.checkArgument(shard >= 0 && shard < shardCount,
            "Shard must be between 0 and %s, got %s", shardCount - 1, shard);
        if (isOmitted(logicalBucket)) {
            return logicalBucket;
        }
        return BucketName.of(logicalBucket.asString() + SEPARATOR + format(shard));
    }

    public List<BucketName> physicalBuckets(BucketName logicalBucket) {
        if (!enabled() || isOmitted(logicalBucket)) {
            return ImmutableList.of(logicalBucket);
        }
        return IntStream.range(0, shardCount)
            .mapToObj(shard -> physicalBucket(logicalBucket, shard))
            .collect(ImmutableList.toImmutableList());
    }

    /**
     * Reverses {@link #physicalBuckets(BucketName)}.
     *
     * @return {@link Optional#empty()} for buckets that do not belong to this layout: the underlying object storage
     * may well hold buckets written before sharding was turned on, or by other tools.
     */
    public Optional<BucketName> logicalBucket(BucketName physicalBucket) {
        if (!enabled() || isOmitted(physicalBucket)) {
            return Optional.of(physicalBucket);
        }
        String value = physicalBucket.asString();
        int separatorPosition = value.length() - shardWidth() - 1;
        if (separatorPosition <= 0 || value.charAt(separatorPosition) != SEPARATOR) {
            return Optional.empty();
        }
        return parseShard(value.substring(separatorPosition + 1))
            .filter(shard -> shard < shardCount)
            .map(shard -> BucketName.of(value.substring(0, separatorPosition)));
    }

    private Optional<Integer> parseShard(String suffix) {
        try {
            return Optional.of(Integer.parseUnsignedInt(suffix));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String format(int shard) {
        String asString = Integer.toString(shard);
        return "0".repeat(shardWidth() - asString.length()) + asString;
    }

    private int shardWidth() {
        return Integer.toString(shardCount - 1).length();
    }
}
