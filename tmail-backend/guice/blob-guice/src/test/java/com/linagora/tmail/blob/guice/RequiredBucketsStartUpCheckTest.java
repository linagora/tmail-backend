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

package com.linagora.tmail.blob.guice;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.api.BucketName;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.blob.bucket.BucketsStartUpCheckConfiguration;
import com.linagora.tmail.blob.bucket.RecordingBucketProvisioner;
import com.linagora.tmail.blob.bucket.RequiredBuckets;

class RequiredBucketsStartUpCheckTest {
    private static final BucketName DEFAULT = BucketName.of("default");
    private static final BucketName UPLOADS = BucketName.of("jmap-uploads");
    private static final BucketName VAULT = BucketName.of("tmail-deleted-message-vault");

    @Test
    void checkShouldProvisionEveryRequiredBucket() {
        RecordingBucketProvisioner provisioner = new RecordingBucketProvisioner();
        RequiredBucketsStartUpCheck testee = new RequiredBucketsStartUpCheck(BucketsStartUpCheckConfiguration.ENABLED, provisioner,
            ImmutableSet.of(() -> ImmutableList.of(DEFAULT), () -> ImmutableList.of(UPLOADS, VAULT)));

        StartUpCheck.CheckResult result = testee.check();

        assertThat(result.isGood()).isTrue();
        assertThat(provisioner.provisioned()).containsExactlyInAnyOrder(DEFAULT, UPLOADS, VAULT);
    }

    @Test
    void checkShouldProvisionBucketsRequiredTwiceOnlyOnce() {
        RecordingBucketProvisioner provisioner = new RecordingBucketProvisioner();
        RequiredBucketsStartUpCheck testee = new RequiredBucketsStartUpCheck(BucketsStartUpCheckConfiguration.ENABLED, provisioner,
            ImmutableSet.of(() -> ImmutableList.of(DEFAULT), () -> ImmutableList.of(DEFAULT, UPLOADS)));

        testee.check();

        assertThat(provisioner.provisioned()).containsExactlyInAnyOrder(DEFAULT, UPLOADS);
    }

    @Test
    void checkShouldBeGoodWhenNoBucketIsRequired() {
        RecordingBucketProvisioner provisioner = new RecordingBucketProvisioner();
        RequiredBucketsStartUpCheck testee = new RequiredBucketsStartUpCheck(BucketsStartUpCheckConfiguration.ENABLED, provisioner,
            ImmutableSet.<RequiredBuckets>of(ImmutableList::of));

        assertThat(testee.check().isGood()).isTrue();
    }

    @Test
    void checkShouldBeBadWhenABucketCannotBeProvisioned() {
        RecordingBucketProvisioner provisioner = new RecordingBucketProvisioner(UPLOADS, VAULT);
        RequiredBucketsStartUpCheck testee = new RequiredBucketsStartUpCheck(BucketsStartUpCheckConfiguration.ENABLED, provisioner,
            ImmutableSet.of(() -> ImmutableList.of(DEFAULT, UPLOADS, VAULT)));

        StartUpCheck.CheckResult result = testee.check();

        assertThat(result.isBad()).isTrue();
        assertThat(result.getName()).isEqualTo(RequiredBucketsStartUpCheck.CHECK_NAME);
        assertThat(result.getDescription()).hasValueSatisfying(description -> assertThat(description)
            .contains("jmap-uploads")
            .contains("tmail-deleted-message-vault")
            .doesNotContain("'default'"));
    }

    @Test
    void checkShouldNotProvisionBucketsWhenDisabled() {
        RecordingBucketProvisioner provisioner = new RecordingBucketProvisioner(UPLOADS);
        RequiredBucketsStartUpCheck testee = new RequiredBucketsStartUpCheck(BucketsStartUpCheckConfiguration.DISABLED, provisioner,
            ImmutableSet.of(() -> ImmutableList.of(DEFAULT, UPLOADS, VAULT)));

        StartUpCheck.CheckResult result = testee.check();

        assertThat(result.isGood()).isTrue();
        assertThat(provisioner.provisioned()).isEmpty();
    }

    @Test
    void checkShouldStillProvisionOtherBucketsWhenOneFails() {
        RecordingBucketProvisioner provisioner = new RecordingBucketProvisioner(UPLOADS);
        RequiredBucketsStartUpCheck testee = new RequiredBucketsStartUpCheck(BucketsStartUpCheckConfiguration.ENABLED, provisioner,
            ImmutableSet.of(() -> ImmutableList.of(DEFAULT, UPLOADS, VAULT)));

        testee.check();

        assertThat(provisioner.provisioned()).containsExactlyInAnyOrder(DEFAULT, VAULT);
    }
}
