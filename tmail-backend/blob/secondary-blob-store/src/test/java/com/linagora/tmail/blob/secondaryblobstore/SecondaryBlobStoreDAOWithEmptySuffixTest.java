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

package com.linagora.tmail.blob.secondaryblobstore;

import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration.UPLOAD_RETRY_EXCEPTION_PREDICATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.blob.objectstorage.aws.S3ClientFactory;
import org.apache.james.blob.objectstorage.aws.S3RequestOption;
import org.apache.james.events.EventBus;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public class SecondaryBlobStoreDAOWithEmptySuffixTest implements BlobStoreDAOContract {
    static DockerAwsS3Container primaryS3 = new DockerAwsS3Container();
    static DockerAwsS3Container secondaryS3 = new DockerAwsS3Container();

    private static final String EMPTY_SECONDARY_BUCKET_NAME_SUFFIX = "";
    private static final BucketName TEST_SECONDARY_BUCKET_NAME = BucketName.of(TEST_BUCKET_NAME.asString() + EMPTY_SECONDARY_BUCKET_NAME_SUFFIX);

    private static S3BlobStoreDAO primaryBlobStoreDAO;
    private static S3BlobStoreDAO secondaryBlobStoreDAO;
    private static SecondaryBlobStoreDAO testee;

    @BeforeAll
    static void beforeAll() {
        primaryS3.start();
        secondaryS3.start();

        primaryBlobStoreDAO = createS3BlobStoreDAO(primaryS3);
        secondaryBlobStoreDAO = createS3BlobStoreDAO(secondaryS3);
        EventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), RetryBackoffConfiguration.DEFAULT, new MemoryEventDeadLetters());
        eventBus.register(new FailedBlobOperationListener(primaryBlobStoreDAO, secondaryBlobStoreDAO, EMPTY_SECONDARY_BUCKET_NAME_SUFFIX));
        testee = new SecondaryBlobStoreDAO(primaryBlobStoreDAO, secondaryBlobStoreDAO, EMPTY_SECONDARY_BUCKET_NAME_SUFFIX, eventBus);
    }

    private static S3BlobStoreDAO createS3BlobStoreDAO(DockerAwsS3Container s3Container) {
        AwsS3AuthConfiguration authConfiguration = AwsS3AuthConfiguration.builder()
            .endpoint(s3Container.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();

        S3BlobStoreConfiguration s3Configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(authConfiguration)
            .region(s3Container.dockerAwsS3().region())
            .uploadRetrySpec(Optional.of(Retry.backoff(3, Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .readTimeout(Optional.of(Duration.ofMillis(500)))
            .build();

        return new S3BlobStoreDAO(new S3ClientFactory(s3Configuration, new RecordingMetricFactory(), new NoopGaugeRegistry()),
            s3Configuration, new TestBlobId.Factory(), S3RequestOption.DEFAULT);
    }

    @AfterEach
    void tearDown() {
        if (primaryS3.isPaused()) {
            primaryS3.unpause();
        }
        if (secondaryS3.isPaused()) {
            secondaryS3.unpause();
        }

        primaryBlobStoreDAO.deleteAllBuckets().block();
        secondaryBlobStoreDAO.deleteAllBuckets().block();
    }

    @AfterAll
    static void afterAll() {
        primaryS3.stop();
        secondaryS3.stop();
    }

    @Override
    public BlobStoreDAO testee() {
        return testee;
    }

    @Test
    public void readShouldReturnInputStreamWhenBlobDoesNotExistInThePrimaryBlobStore() {
        Mono.from(secondaryBlobStoreDAO.save(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(testee.read(TEST_BUCKET_NAME, TEST_BLOB_ID))
            .hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    public void readReactiveShouldReturnDataWhenBlobDoesNotExistInThePrimaryBlobStore() {
        Mono.from(secondaryBlobStoreDAO.save(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(testee.readReactive(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    public void readReactiveShouldReturnDataWhenPrimaryBlobStoreIsDown() {
        Mono.from(primaryBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(secondaryBlobStoreDAO.save(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        primaryS3.pause();

        assertThat(Mono.from(testee.readReactive(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    public void readReactiveShouldReturnDataWhenSecondBlobStoreIsDown() {
        Mono.from(primaryBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(secondaryBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        secondaryS3.pause();

        assertThat(Mono.from(testee.readReactive(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    public void readReactiveShouldThrowExceptionWhenBothBlobStoreIsDown() {
        Mono.from(primaryBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(secondaryBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        primaryS3.pause();
        secondaryS3.pause();

        assertThatThrownBy(() -> Mono.from(testee.readReactive(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isInstanceOf(ObjectStoreException.class)
            .hasStackTraceContaining("Unable to execute HTTP request: Read timed out");
    }

    @Test
    public void readBytesShouldReturnDataWhenBlobDoesNotExistInThePrimaryBlobStore() {
        Mono.from(secondaryBlobStoreDAO.save(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveBytesShouldSaveDataToBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(primaryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondaryBlobStoreDAO.readBytes(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveBytesShouldEventuallySaveDataToBothBlobStoresWhenPrimaryStorageIsDown() {
        primaryS3.pause();
        unpauseS3AfterAwhile(primaryS3);
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(primaryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondaryBlobStoreDAO.readBytes(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveBytesShouldEventuallySaveDataToBothBlobStoresWhenSecondStorageIsDown() {
        secondaryS3.pause();
        unpauseS3AfterAwhile(secondaryS3);
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(primaryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondaryBlobStoreDAO.readBytes(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveInputStreamShouldSaveDataToBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(SHORT_BYTEARRAY))).block();

        assertThat(Mono.from(primaryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondaryBlobStoreDAO.readBytes(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveInputStreamShouldEventuallySaveDataToBothBlobStoresWhenPrimaryStorageIsDown() {
        primaryS3.pause();
        unpauseS3AfterAwhile(primaryS3);
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(SHORT_BYTEARRAY))).block();

        assertThat(Mono.from(primaryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondaryBlobStoreDAO.readBytes(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveInputStreamShouldEventuallySaveDataToBothBlobStoresWhenSecondStorageIsDown() {
        secondaryS3.pause();
        unpauseS3AfterAwhile(secondaryS3);
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(SHORT_BYTEARRAY))).block();

        assertThat(Mono.from(primaryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondaryBlobStoreDAO.readBytes(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveByteSourceShouldSaveDataToBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();

        assertThat(Mono.from(primaryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondaryBlobStoreDAO.readBytes(TEST_SECONDARY_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void deleteBlobShouldDeleteInBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();
        Mono.from(testee.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(Flux.from(primaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBlobShouldEventuallyDeleteInBothBlobStoresWhenPrimaryStorageIsDown() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();

        primaryS3.pause();
        unpauseS3AfterAwhile(primaryS3);
        Mono.from(testee.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(Flux.from(primaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBlobShouldEventuallyDeleteInBothBlobStoresWhenSecondStorageIsDown() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();

        secondaryS3.pause();
        unpauseS3AfterAwhile(secondaryS3);
        Mono.from(testee.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(Flux.from(primaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBlobsShouldDeleteInBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();
        Mono.from(testee.delete(TEST_BUCKET_NAME, ImmutableList.of(TEST_BLOB_ID))).block();

        assertThat(Flux.from(primaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBlobsShouldEventuallyDeleteInBothBlobStoresWhenPrimaryStorageIsDown() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();

        primaryS3.pause();
        unpauseS3AfterAwhile(primaryS3);
        Mono.from(testee.delete(TEST_BUCKET_NAME, ImmutableList.of(TEST_BLOB_ID))).block();

        assertThat(Flux.from(primaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBlobsShouldEventuallyDeleteInBothBlobStoresWhenSecondStorageIsDown() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();

        secondaryS3.pause();
        unpauseS3AfterAwhile(secondaryS3);
        Mono.from(testee.delete(TEST_BUCKET_NAME, ImmutableList.of(TEST_BLOB_ID))).block();

        assertThat(Flux.from(primaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondaryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBucketShouldDeleteInBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();
        Mono.from(testee.deleteBucket(TEST_BUCKET_NAME)).block();

        assertThat(Flux.from(primaryBlobStoreDAO.listBuckets()).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondaryBlobStoreDAO.listBuckets()).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBucketShouldEventuallyDeleteInBothBlobStoresWhenPrimaryStorageIsDown() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();

        primaryS3.pause();
        unpauseS3AfterAwhile(primaryS3);
        Mono.from(testee.deleteBucket(TEST_BUCKET_NAME)).block();

        assertThat(Flux.from(primaryBlobStoreDAO.listBuckets()).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondaryBlobStoreDAO.listBuckets()).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBucketShouldEventuallyDeleteInBothBlobStoresWhenSecondStorageIsDown() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();

        secondaryS3.pause();
        unpauseS3AfterAwhile(secondaryS3);
        Mono.from(testee.deleteBucket(TEST_BUCKET_NAME)).block();

        assertThat(Flux.from(primaryBlobStoreDAO.listBuckets()).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondaryBlobStoreDAO.listBuckets()).collectList().block())
            .isEmpty();
    }

    @Test
    @Override
    public void saveInputStreamShouldThrowOnIOException() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, getThrowingInputStream())).block())
            .getCause()
            .isInstanceOf(IOException.class);
    }

    @Test
    @Override
    public void saveByteSourceShouldThrowOnIOException() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return getThrowingInputStream();
            }
        })).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    private void unpauseS3AfterAwhile(DockerAwsS3Container s3) {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            s3.unpause();
        }).start();
    }
}
