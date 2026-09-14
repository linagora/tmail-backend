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

import com.linagora.tmail.blob.secondaryblobstore.SecondaryBlobStoreDAO;

import reactor.core.publisher.Mono;

/**
 * Provisions the bucket on both object storages, the secondary one using the suffixed bucket name.
 *
 * @see SecondaryBlobStoreDAO
 */
public class SecondaryBucketProvisioner implements BucketProvisioner {
    private final BucketProvisioner primary;
    private final BucketProvisioner secondary;
    private final String secondaryBucketSuffix;

    public SecondaryBucketProvisioner(BucketProvisioner primary, BucketProvisioner secondary, String secondaryBucketSuffix) {
        this.primary = primary;
        this.secondary = secondary;
        this.secondaryBucketSuffix = secondaryBucketSuffix;
    }

    @Override
    public Mono<Void> ensureExists(BucketName bucketName) {
        return Mono.when(primary.ensureExists(bucketName),
            secondary.ensureExists(BucketName.of(bucketName.asString() + secondaryBucketSuffix)));
    }
}
