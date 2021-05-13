package com.linagora.tmail.blob.blobid.list

import com.linagora.tmail.blob.blobid.list.SingleSaveBlobStoreDAOFixture._
import org.apache.james.blob.api.{BlobId, BucketName, ObjectStoreException}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

trait SingleSaveBlobStoreContract {

  def singleSaveBlobStoreDAO: SingleSaveBlobStoreDAO

  def blobIdList: BlobIdList

  def blobIdFactory: BlobId.Factory

  def defaultBucketName: BucketName

  @Test
  def saveShouldSuccessIfBlobIDoesNotExist(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()
    val isStore: Boolean = SMono.fromPublisher(blobIdList.isStored(blobId)).block()
    assert(isStore)
  }

  @Test
  def saveShouldFailIfBlobExists(): Unit = {
    val blobId: BlobId = blobIdFactory.randomId()
    SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    val duplicateBlobThrowingCallable: ThrowingCallable = () =>
      SMono.fromPublisher(singleSaveBlobStoreDAO.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    assertThatThrownBy(duplicateBlobThrowingCallable)
      .isInstanceOf(classOf[ObjectStoreException])
    assertThatThrownBy(duplicateBlobThrowingCallable)
      .hasMessage("Can not save duplicate blob when single save is enabled")
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
