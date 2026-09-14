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

package com.linagora.tmail.blob.bucket;

import org.apache.james.blob.api.BucketName;

import reactor.core.publisher.Mono;

/**
 * Makes sure the physical buckets backing a logical {@link BucketName} exist, creating the missing ones.
 *
 * <p>The object storage creates buckets lazily upon the first write, which defers failures (missing permissions,
 * invalid names...) to runtime, and has concurrent writers race to create the same bucket. Provisioning the known
 * buckets at startup turns those into a boot time failure.</p>
 *
 * <p>Implementations mirror the bucket naming of the blobStore layers: sharding, secondary blob store, S3 prefix and
 * namespace.</p>
 */
@FunctionalInterface
public interface BucketProvisioner {
    /**
     * For blob stores without a notion of bucket needing to be created beforehand (file, Postgres).
     */
    BucketProvisioner NOOP = bucketName -> Mono.empty();

    Mono<Void> ensureExists(BucketName bucketName);
}
