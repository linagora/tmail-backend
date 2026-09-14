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

import com.linagora.tmail.blob.sharding.ShardedBlobStoreDAO;
import com.linagora.tmail.blob.sharding.TmailBlobStoreShardingConfiguration;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Provisions every shard of a logical bucket.
 *
 * @see ShardedBlobStoreDAO
 */
public class ShardedBucketProvisioner implements BucketProvisioner {
    private static final int CONCURRENCY = 8;

    private final BucketProvisioner delegate;
    private final TmailBlobStoreShardingConfiguration configuration;

    public ShardedBucketProvisioner(BucketProvisioner delegate, TmailBlobStoreShardingConfiguration configuration) {
        this.delegate = delegate;
        this.configuration = configuration;
    }

    @Override
    public Mono<Void> ensureExists(BucketName bucketName) {
        return Flux.fromIterable(configuration.physicalBuckets(bucketName))
            .flatMap(delegate::ensureExists, CONCURRENCY)
            .then();
    }
}
