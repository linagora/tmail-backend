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

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * Maps a James logical {@link BucketName} onto a fixed set of physical buckets, the shard being derived from the
 * {@link BlobId}.
 *
 * <p>S3 compatibility is an API contract, not a behaviour contract: a Ceph / Rados gateway bucket is backed by
 * sharded omap indexes that should not hold much more than 100K entries each. Holding billions of objects in a
 * single bucket would require tens of thousands of index shards, and an <strong>ordered</strong> S3 listing would
 * then need to merge all of them, which does not hold. Splitting the objects across several buckets keeps each
 * bucket index within a listable size.</p>
 *
 * <p>Physical buckets are named {@code <logical bucket>-<zero padded shard number>}, eg. {@code default-000} up to
 * {@code default-255} for 256 shards. <strong>The shard count is written in stone</strong>: changing it relocates
 * every blob and makes the already written ones unreadable. Because blobIds are content addressed, deduplication
 * and garbage collection still hold within a shard.</p>
 *
 * <p>Buckets that stay small enough to be listed as they are - the JMAP uploads and the mails in transit, say - can
 * be left out of the layout entirely, and are then stored under their plain name.</p>
 */
public record BucketSharding(int shardCount, Set<BucketName> omittedBuckets) {
    public static final String SHARD_COUNT_PROPERTY = "tmail.blobstore.shards";
    public static final String OMITTED_BUCKETS_PROPERTY = "tmail.blobstore.shards.ommited.buckets";
    public static final char SEPARATOR = '-';

    private static final Splitter OMITTED_BUCKETS_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

    /**
     * Murmur3 is stable across JVMs and Guava releases, which {@link String#hashCode()} guarantees too but with a
     * far poorer distribution over hexadecimal blobIds.
     */
    private static final HashFunction HASH_FUNCTION = Hashing.murmur3_32_fixed();

    /**
     * @return the sharding configured by {@code -Dtmail.blobstore.shards=256} and
     * {@code -Dtmail.blobstore.shards.ommited.buckets=jmap-uploads,mail-processing}, or {@link Optional#empty()} when
     * the shard count property is omitted, in which case no sharding is applied at all.
     */
    public static Optional<BucketSharding> fromSystemProperties() {
        return Optional.ofNullable(System.getProperty(SHARD_COUNT_PROPERTY))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(shardCount -> parse(shardCount, System.getProperty(OMITTED_BUCKETS_PROPERTY, "")));
    }

    private static BucketSharding parse(String shardCount, String omittedBuckets) {
        try {
            return new BucketSharding(Integer.parseInt(shardCount), parseOmittedBuckets(omittedBuckets));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(SHARD_COUNT_PROPERTY + " must be a positive integer, got " + shardCount, e);
        }
    }

    private static Set<BucketName> parseOmittedBuckets(String omittedBuckets) {
        return OMITTED_BUCKETS_SPLITTER.splitToStream(omittedBuckets)
            .map(BucketName::of)
            .collect(ImmutableSet.toImmutableSet());
    }

    public BucketSharding(int shardCount) {
        this(shardCount, ImmutableSet.of());
    }

    public BucketSharding {
        Preconditions.checkArgument(shardCount > 0, "%s must be strictly positive", SHARD_COUNT_PROPERTY);
        omittedBuckets = ImmutableSet.copyOf(omittedBuckets);
    }

    /**
     * @return whether this bucket is left out of the layout, and thus stored under its plain name.
     */
    public boolean isOmitted(BucketName logicalBucket) {
        return omittedBuckets.contains(logicalBucket);
    }

    public int shardOf(BlobId blobId) {
        return Math.floorMod(HASH_FUNCTION.hashString(blobId.asString(), StandardCharsets.UTF_8).asInt(), shardCount);
    }

    public BucketName physicalBucket(BucketName logicalBucket, BlobId blobId) {
        if (isOmitted(logicalBucket)) {
            return logicalBucket;
        }
        return physicalBucket(logicalBucket, shardOf(blobId));
    }

    public BucketName physicalBucket(BucketName logicalBucket, int shard) {
        return BucketName.of(logicalBucket.asString() + SEPARATOR + format(shard));
    }

    public List<BucketName> physicalBuckets(BucketName logicalBucket) {
        if (isOmitted(logicalBucket)) {
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
        if (isOmitted(physicalBucket)) {
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
