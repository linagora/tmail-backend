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
import java.util.Optional;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.blob.objectstorage.aws.S3ClientFactory;
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

public class SecondaryBlobStoreDAOTest implements BlobStoreDAOContract {
    static DockerAwsS3Container primaryS3 = new DockerAwsS3Container();
    static DockerAwsS3Container secondaryS3 = new DockerAwsS3Container();

    private static S3BlobStoreDAO firstBlobStoreDAO;
    private static S3BlobStoreDAO secondBlobStoreDAO;
    private static SecondaryBlobStoreDAO testee;

    @BeforeAll
    static void beforeAll() {
        primaryS3.start();
        secondaryS3.start();

        firstBlobStoreDAO = createS3BlobStoreDAO(primaryS3);
        secondBlobStoreDAO = createS3BlobStoreDAO(secondaryS3);
        testee = new SecondaryBlobStoreDAO(firstBlobStoreDAO, secondBlobStoreDAO);
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
            .uploadRetrySpec(Optional.of(Retry.backoff(3, java.time.Duration.ofSeconds(1))
                .filter(UPLOAD_RETRY_EXCEPTION_PREDICATE)))
            .build();

        return new S3BlobStoreDAO(new S3ClientFactory(s3Configuration, new RecordingMetricFactory(), new NoopGaugeRegistry()), s3Configuration, new TestBlobId.Factory());
    }

    @AfterEach
    void tearDown() {
        firstBlobStoreDAO.deleteAllBuckets().block();
        secondBlobStoreDAO.deleteAllBuckets().block();
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

    @Override
    @Disabled("Not supported")
    public void listBucketsShouldReturnBucketsWithNoBlob() {
    }

    @Test
    public void readShouldReturnInputStreamWhenFailToReadFromFirstBlobStore() {
        Mono.from(secondBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(testee.read(TEST_BUCKET_NAME, TEST_BLOB_ID))
            .hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    public void readReactiveShouldReturnDataWhenFailToReadFromFirstBlobStore() {
        Mono.from(secondBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(testee.readReactive(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    public void readBytesShouldReturnDataWhenFailToReadFromFirstBlobStore() {
        Mono.from(secondBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(testee.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveBytesShouldSaveDataToBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(firstBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveInputStreamShouldSaveDataToBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(SHORT_BYTEARRAY))).block();

        assertThat(Mono.from(firstBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveByteSourceShouldSaveDataToBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();

        assertThat(Mono.from(firstBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void deleteBlobShouldDeleteInBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();
        Mono.from(testee.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(Flux.from(firstBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBlobsShouldDeleteInBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();
        Mono.from(testee.delete(TEST_BUCKET_NAME, ImmutableList.of(TEST_BLOB_ID))).block();

        assertThat(Flux.from(firstBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBucketShouldDeleteInBothBlobStores() {
        Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();
        Mono.from(testee.deleteBucket(TEST_BUCKET_NAME)).block();

        assertThat(Flux.from(firstBlobStoreDAO.listBuckets()).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondBlobStoreDAO.listBuckets()).collectList().block())
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
    public void saveShouldThrowWhenNullData() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, (byte[]) null)).block())
            .isInstanceOf(ObjectStoreException.class);
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

}
