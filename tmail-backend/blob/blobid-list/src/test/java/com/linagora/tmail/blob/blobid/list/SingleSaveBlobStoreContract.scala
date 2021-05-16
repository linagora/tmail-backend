package com.linagora.tmail.blob.blobid.list

import com.google.common.io.ByteSource
import org.apache.james.blob.api.BlobStoreDAOFixture.{SHORT_BYTEARRAY, TEST_BUCKET_NAME}
import org.apache.james.blob.api.{BlobId, BlobStoreDAOContract, BucketName, ObjectStoreException}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

import java.io.ByteArrayInputStream

trait SingleSaveBlobStoreContract extends BlobStoreDAOContract {

  def singleSaveBlobStoreDAO: SingleSaveBlobStoreDAO

  def blobIdList: BlobIdList

  def blobIdFactory: BlobId.Factory

  def defaultBucketName: BucketName

  @Test
  def saveBytesShouldSuccessIfBlobIdDoesNotExist(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    assertThat(SMono.fromPublisher(blobIdList.isStored(blobId)).block()).isTrue
  }

  @Test
  def saveBytesShouldNoopIfBlobExists(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    assertThat(SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block())
  }

  @Test
  def saveInputStreamShouldSuccessIfBlobIdDoesNotExist(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, new ByteArrayInputStream(SHORT_BYTEARRAY))).block()

    assertThat(SMono.fromPublisher(blobIdList.isStored(blobId)).block()).isTrue
  }

  @Test
  def saveInputStreamShouldNoopIfBlobExists(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, new ByteArrayInputStream(SHORT_BYTEARRAY))).block()

    assertThat(SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, new ByteArrayInputStream(SHORT_BYTEARRAY))).block())
  }

  @Test
  def saveByteSourceShouldSuccessIfBlobIdDoesNotExist(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, ByteSource.wrap(SHORT_BYTEARRAY))).block()

    assertThat(SMono.fromPublisher(blobIdList.isStored(blobId)).block()).isTrue
  }

  @Test
  def saveByteSourceShouldNoopIfBlobExists(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, ByteSource.wrap(SHORT_BYTEARRAY))).block()

    assertThat(SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, ByteSource.wrap(SHORT_BYTEARRAY))).block())
  }

  @Test
  def deleteShouldFailWithDefaultBucket(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(defaultBucketName, blobId, SHORT_BYTEARRAY)).block()

    val deleteBlobThrowingCallable: ThrowingCallable = () =>
      SMono.fromPublisher(singleSaveBlobStoreDAO.delete(defaultBucketName, blobId)).block()

    assertThatThrownBy(deleteBlobThrowingCallable)
      .isInstanceOf(classOf[ObjectStoreException])
    assertThatThrownBy(deleteBlobThrowingCallable)
      .hasMessage("Can not delete in the default bucket when single save is enabled")
  }

  @Test
  def deleteShouldSuccessWithNotDefaultBucket(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    assertThat(SMono.fromPublisher(singleSaveBlobStoreDAO.delete(TEST_BUCKET_NAME, blobId)).block())
  }
}
