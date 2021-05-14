package com.linagora.tmail.blob.blobid.list

import com.linagora.tmail.blob.blobid.list.SingleSaveBlobStoreDAOFixture._
import org.apache.james.blob.api.{BlobId, BucketName, ObjectStoreException}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono
import org.apache.james.blob.api.BlobStoreDAOContract

trait SingleSaveBlobStoreContract extends BlobStoreDAOContract {

  def singleSaveBlobStoreDAO: SingleSaveBlobStoreDAO

  def blobIdList: BlobIdList

  def blobIdFactory: BlobId.Factory

  def defaultBucketName: BucketName

  @Test
  def saveShouldSuccessIfBlobIdDoesNotExist(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    assertThat(SMono.fromPublisher(blobIdList.isStored(blobId)).block()).isTrue
  }

  @Test
  def saveShouldNoopIfBlobExists(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    assertThat(SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block())
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
