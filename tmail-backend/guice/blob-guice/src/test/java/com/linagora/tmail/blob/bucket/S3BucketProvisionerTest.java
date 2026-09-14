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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3ClientFactory;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

class S3BucketProvisionerTest {
    private static final String PREFIX = "prefix-";

    static DockerAwsS3Container s3 = new DockerAwsS3Container();

    static S3ClientFactory clientFactory;

    @BeforeAll
    static void beforeAll() {
        s3.start();
        clientFactory = clientFactory(configuration(DockerAwsS3Container.SECRET_ACCESS_KEY, Optional.empty(), Optional.empty()));
    }

    @AfterAll
    static void afterAll() {
        clientFactory.close();
        s3.stop();
    }

    private static S3BlobStoreConfiguration configuration(String secretKey, Optional<BucketName> namespace, Optional<String> prefix) {
        return S3BlobStoreConfiguration.builder()
            .authConfiguration(AwsS3AuthConfiguration.builder()
                .endpoint(s3.getEndpoint())
                .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
                .secretKey(secretKey)
                .build())
            .region(DockerAwsS3Container.REGION)
            .defaultBucketName(namespace)
            .bucketPrefix(prefix)
            .build();
    }

    private static S3ClientFactory clientFactory(S3BlobStoreConfiguration configuration) {
        return new S3ClientFactory(configuration, new RecordingMetricFactory(), new NoopGaugeRegistry());
    }

    private static BucketName randomBucket() {
        return BucketName.of("bucket-" + UUID.randomUUID());
    }

    private static List<String> physicalBuckets() {
        return Mono.fromFuture(() -> clientFactory.get().listBuckets())
            .flatMapIterable(ListBucketsResponse::buckets)
            .map(Bucket::name)
            .collectList()
            .block();
    }

    @Test
    void ensureExistsShouldCreateMissingBucket() {
        BucketName bucket = randomBucket();
        S3BucketProvisioner testee = new S3BucketProvisioner(clientFactory, configuration(DockerAwsS3Container.SECRET_ACCESS_KEY, Optional.empty(), Optional.empty()));

        testee.ensureExists(bucket).block();

        assertThat(physicalBuckets()).contains(bucket.asString());
    }

    @Test
    void ensureExistsShouldBeIdempotent() {
        BucketName bucket = randomBucket();
        S3BucketProvisioner testee = new S3BucketProvisioner(clientFactory, configuration(DockerAwsS3Container.SECRET_ACCESS_KEY, Optional.empty(), Optional.empty()));

        testee.ensureExists(bucket).block();

        assertThatCode(() -> testee.ensureExists(bucket).block())
            .doesNotThrowAnyException();
    }

    @Test
    void ensureExistsShouldTolerateConcurrentCreations() {
        BucketName bucket = randomBucket();
        S3BucketProvisioner testee = new S3BucketProvisioner(clientFactory, configuration(DockerAwsS3Container.SECRET_ACCESS_KEY, Optional.empty(), Optional.empty()));

        assertThatCode(() -> Flux.range(0, 10)
                .flatMap(i -> testee.ensureExists(bucket))
                .then()
                .block())
            .doesNotThrowAnyException();
        assertThat(physicalBuckets()).contains(bucket.asString());
    }

    @Test
    void ensureExistsShouldApplyBucketPrefix() {
        BucketName bucket = randomBucket();
        S3BucketProvisioner testee = new S3BucketProvisioner(clientFactory, configuration(DockerAwsS3Container.SECRET_ACCESS_KEY, Optional.empty(), Optional.of(PREFIX)));

        testee.ensureExists(bucket).block();

        assertThat(physicalBuckets())
            .contains(PREFIX + bucket.asString())
            .doesNotContain(bucket.asString());
    }

    @Test
    void ensureExistsShouldNotPrefixTheNamespace() {
        BucketName namespace = randomBucket();
        S3BucketProvisioner testee = new S3BucketProvisioner(clientFactory, configuration(DockerAwsS3Container.SECRET_ACCESS_KEY, Optional.of(namespace), Optional.of(PREFIX)));

        testee.ensureExists(namespace).block();

        assertThat(physicalBuckets())
            .contains(namespace.asString())
            .doesNotContain(PREFIX + namespace.asString());
    }

    @Test
    void ensureExistsShouldFailWithAClearMessageWhenTheObjectStorageRejectsIt() {
        BucketName bucket = randomBucket();
        S3BlobStoreConfiguration wrongCredentials = configuration("wrongSecretKey", Optional.empty(), Optional.empty());
        S3ClientFactory wrongCredentialsClientFactory = clientFactory(wrongCredentials);

        try {
            S3BucketProvisioner testee = new S3BucketProvisioner(wrongCredentialsClientFactory, wrongCredentials);

            assertThatThrownBy(() -> testee.ensureExists(bucket).block())
                .isInstanceOf(ObjectStoreException.class)
                .hasMessageContaining(bucket.asString())
                .hasMessageContaining(s3.getEndpoint().toString());
            assertThat(physicalBuckets()).doesNotContain(bucket.asString());
        } finally {
            wrongCredentialsClientFactory.close();
        }
    }
}
