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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectStoreException;
import org.junit.jupiter.api.Test;

class SecondaryBucketProvisionerTest {
    private static final BucketName BUCKET = BucketName.of("default");
    private static final String SUFFIX = "-copy";

    @Test
    void ensureExistsShouldProvisionBothObjectStorages() {
        RecordingBucketProvisioner primary = new RecordingBucketProvisioner();
        RecordingBucketProvisioner secondary = new RecordingBucketProvisioner();
        SecondaryBucketProvisioner testee = new SecondaryBucketProvisioner(primary, secondary, SUFFIX);

        testee.ensureExists(BUCKET).block();

        assertThat(primary.provisioned()).containsExactly(BUCKET);
        assertThat(secondary.provisioned()).containsExactly(BucketName.of("default-copy"));
    }

    @Test
    void ensureExistsShouldSupportEmptySuffix() {
        RecordingBucketProvisioner primary = new RecordingBucketProvisioner();
        RecordingBucketProvisioner secondary = new RecordingBucketProvisioner();
        SecondaryBucketProvisioner testee = new SecondaryBucketProvisioner(primary, secondary, "");

        testee.ensureExists(BUCKET).block();

        assertThat(secondary.provisioned()).containsExactly(BUCKET);
    }

    @Test
    void ensureExistsShouldFailWhenTheSecondaryObjectStorageFails() {
        RecordingBucketProvisioner primary = new RecordingBucketProvisioner();
        RecordingBucketProvisioner secondary = new RecordingBucketProvisioner(BucketName.of("default-copy"));
        SecondaryBucketProvisioner testee = new SecondaryBucketProvisioner(primary, secondary, SUFFIX);

        assertThatThrownBy(() -> testee.ensureExists(BUCKET).block())
            .isInstanceOf(ObjectStoreException.class)
            .hasMessageContaining("default-copy");
    }
}
