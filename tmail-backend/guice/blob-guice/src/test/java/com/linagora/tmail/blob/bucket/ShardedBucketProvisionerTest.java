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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.api.BucketName;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.blob.sharding.TmailBlobStoreShardingConfiguration;

class ShardedBucketProvisionerTest {
    private static final BucketName BUCKET = BucketName.of("default");
    private static final BucketName OMITTED = BucketName.of("jmap-uploads");

    @Test
    void ensureExistsShouldProvisionEveryShard() {
        RecordingBucketProvisioner delegate = new RecordingBucketProvisioner();
        ShardedBucketProvisioner testee = new ShardedBucketProvisioner(delegate, TmailBlobStoreShardingConfiguration.of(3));

        testee.ensureExists(BUCKET).block();

        assertThat(delegate.provisioned())
            .containsExactlyInAnyOrder(BucketName.of("default-0"), BucketName.of("default-1"), BucketName.of("default-2"));
    }

    @Test
    void ensureExistsShouldProvisionOmittedBucketsUnderTheirPlainName() {
        RecordingBucketProvisioner delegate = new RecordingBucketProvisioner();
        ShardedBucketProvisioner testee = new ShardedBucketProvisioner(delegate, TmailBlobStoreShardingConfiguration.of(3, OMITTED));

        testee.ensureExists(OMITTED).block();

        assertThat(delegate.provisioned())
            .containsExactly(OMITTED);
    }
}
