package com.linagora.tmail.blob.secondaryblobstore;

import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_STRING;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SecondaryBlobStoreDAOTest implements BlobStoreDAOContract {
    private SecondaryBlobStoreDAO blobStoreDAO;
    private MemoryBlobStoreDAO firstMemoryBlobStoreDAO;
    private MemoryBlobStoreDAO secondMemoryBlobStoreDAO;

    @BeforeEach
    void setUp() {
        firstMemoryBlobStoreDAO = new MemoryBlobStoreDAO();
        secondMemoryBlobStoreDAO = new MemoryBlobStoreDAO();
        blobStoreDAO = new SecondaryBlobStoreDAO(firstMemoryBlobStoreDAO, secondMemoryBlobStoreDAO);
    }

    @Override
    public BlobStoreDAO testee() {
        return blobStoreDAO;
    }

    @Override
    @Disabled("Not supported")
    public void listBucketsShouldReturnBucketsWithNoBlob() {
    }

    @Test
    public void readShouldReturnInputStreamWhenFailToReadFromFirstBlobStore() {
        Mono.from(secondMemoryBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(blobStoreDAO.read(TEST_BUCKET_NAME, TEST_BLOB_ID))
            .hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    public void readReactiveShouldReturnDataWhenFailToReadFromFirstBlobStore() {
        Mono.from(secondMemoryBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(blobStoreDAO.readReactive(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    public void readBytesShouldReturnDataWhenFailToReadFromFirstBlobStore() {
        Mono.from(secondMemoryBlobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(blobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveBytesShouldSaveDataToBothBlobStores() {
        Mono.from(blobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Mono.from(firstMemoryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondMemoryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveInputStreamShouldSaveDataToBothBlobStores() {
        Mono.from(blobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, new ByteArrayInputStream(SHORT_BYTEARRAY))).block();

        assertThat(Mono.from(firstMemoryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondMemoryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void saveByteSourceShouldSaveDataToBothBlobStores() {
        Mono.from(blobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();

        assertThat(Mono.from(firstMemoryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
        assertThat(Mono.from(secondMemoryBlobStoreDAO.readBytes(TEST_BUCKET_NAME, TEST_BLOB_ID)).block())
            .isEqualTo(SHORT_BYTEARRAY);
    }

    @Test
    public void deleteBlobShouldDeleteInBothBlobStores() {
        Mono.from(blobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();
        Mono.from(blobStoreDAO.delete(TEST_BUCKET_NAME, TEST_BLOB_ID)).block();

        assertThat(Flux.from(firstMemoryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondMemoryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBlobsShouldDeleteInBothBlobStores() {
        Mono.from(blobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();
        Mono.from(blobStoreDAO.delete(TEST_BUCKET_NAME, ImmutableList.of(TEST_BLOB_ID))).block();

        assertThat(Flux.from(firstMemoryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondMemoryBlobStoreDAO.listBlobs(TEST_BUCKET_NAME)).collectList().block())
            .isEmpty();
    }

    @Test
    public void deleteBucketShouldDeleteInBothBlobStores() {
        Mono.from(blobStoreDAO.save(TEST_BUCKET_NAME, TEST_BLOB_ID, ByteSource.wrap(SHORT_BYTEARRAY))).block();
        Mono.from(blobStoreDAO.deleteBucket(TEST_BUCKET_NAME)).block();

        assertThat(Flux.from(firstMemoryBlobStoreDAO.listBuckets()).collectList().block())
            .isEmpty();
        assertThat(Flux.from(secondMemoryBlobStoreDAO.listBuckets()).collectList().block())
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
    public void saveStringShouldThrowWhenNullBucketName() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(null, TEST_BLOB_ID, SHORT_STRING)).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    @Override
    public void saveBytesShouldThrowWhenNullBucketName() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(null, TEST_BLOB_ID, SHORT_BYTEARRAY)).block())
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

    @Test
    @Override
    public void saveInputStreamShouldThrowWhenNullBucketName() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(null, TEST_BLOB_ID, new ByteArrayInputStream(SHORT_BYTEARRAY))).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    @Override
    public void saveShouldThrowWhenNullByteSource() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, (ByteSource) null)).block())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    @Override
    public void deleteBucketShouldThrowWhenNullBucketName() {
        BlobStoreDAO store = testee();

        assertThatThrownBy(() -> Mono.from(store.deleteBucket(null)).block())
            .isInstanceOf(ObjectStoreException.class);
    }
}
