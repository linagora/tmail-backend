package com.linagora.tmail.blob.blobid.list

import java.io.ByteArrayInputStream
import java.util.UUID

import com.google.common.io.ByteSource
import org.apache.james.blob.api.BlobStoreDAOFixture.{SHORT_BYTEARRAY, TEST_BUCKET_NAME}
import org.apache.james.blob.api.{BlobId, BlobStoreDAOContract, BucketName, ObjectStoreException}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

trait SingleSaveBlobStoreContract extends BlobStoreDAOContract {

  def blobIdList: BlobIdList

  def blobIdFactory: BlobId.Factory

  def defaultBucketName: BucketName

  @Test
  def saveBytesShouldSuccessIfBlobIdDoesNotExist(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID.toString)
    SMono.fromPublisher(testee.save(defaultBucketName, blobId, SHORT_BYTEARRAY)).block()

    assertThat(SMono.fromPublisher(blobIdList.isStored(blobId)).block()).isTrue
  }

  @Test
  def saveBytesShouldNoopIfBlobExists(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID.toString)
    SMono.fromPublisher(testee.save(defaultBucketName, blobId, SHORT_BYTEARRAY)).block()

    assertThatCode(() => SMono.fromPublisher(testee.save(defaultBucketName, blobId, SHORT_BYTEARRAY)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def saveInputStreamShouldSuccessIfBlobIdDoesNotExist(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID.toString)
    SMono.fromPublisher(testee.save(defaultBucketName, blobId, new ByteArrayInputStream(SHORT_BYTEARRAY))).block()

    assertThat(SMono.fromPublisher(blobIdList.isStored(blobId)).block()).isTrue
  }

  @Test
  def saveInputStreamShouldNoopIfBlobExists(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID.toString)
    SMono.fromPublisher(testee.save(defaultBucketName, blobId, new ByteArrayInputStream(SHORT_BYTEARRAY))).block()

    assertThatCode(() => SMono.fromPublisher(testee.save(defaultBucketName, blobId, new ByteArrayInputStream(SHORT_BYTEARRAY))).block())
      .doesNotThrowAnyException()
  }

  @Test
  def saveByteSourceShouldSuccessIfBlobIdDoesNotExist(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID.toString)
    SMono.fromPublisher(testee.save(defaultBucketName, blobId, ByteSource.wrap(SHORT_BYTEARRAY))).block()

    assertThat(SMono.fromPublisher(blobIdList.isStored(blobId)).block()).isTrue
  }

  @Test
  def saveByteSourceShouldNoopIfBlobExists(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID.toString)
    SMono.fromPublisher(testee.save(defaultBucketName, blobId, ByteSource.wrap(SHORT_BYTEARRAY))).block()

    assertThatCode(() => SMono.fromPublisher(testee.save(defaultBucketName, blobId, ByteSource.wrap(SHORT_BYTEARRAY))).block())
      .doesNotThrowAnyException()
  }

  @Test
  def saveBytesShouldSucceedInOtherBucketWhenBlobAlreadyInDefaultOne(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID.toString)
    SMono.fromPublisher(testee.save(defaultBucketName, blobId, SHORT_BYTEARRAY)).block()

    SMono.fromPublisher(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    assertThat(SMono.fromPublisher(testee.readBytes(TEST_BUCKET_NAME, blobId)).block())
      .isEqualTo(SHORT_BYTEARRAY)
  }

  @Test
  def saveInputStreamShouldSucceedInOtherBucketWhenBlobAlreadyInDefaultOne(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID.toString)
    SMono.fromPublisher(testee.save(defaultBucketName, blobId, new ByteArrayInputStream(SHORT_BYTEARRAY))).block()

    SMono.fromPublisher(testee.save(TEST_BUCKET_NAME, blobId, new ByteArrayInputStream(SHORT_BYTEARRAY))).block()

    assertThat(SMono.fromPublisher(testee.readBytes(TEST_BUCKET_NAME, blobId)).block())
      .isEqualTo(SHORT_BYTEARRAY)
  }

  @Test
  def saveByteSourceShouldSucceedInOtherBucketWhenBlobAlreadyInDefaultOne(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID.toString)
    SMono.fromPublisher(testee.save(defaultBucketName, blobId, ByteSource.wrap(SHORT_BYTEARRAY))).block()

    SMono.fromPublisher(testee.save(TEST_BUCKET_NAME, blobId, ByteSource.wrap(SHORT_BYTEARRAY))).block()

    assertThat(SMono.fromPublisher(testee.readBytes(TEST_BUCKET_NAME, blobId)).block())
      .isEqualTo(SHORT_BYTEARRAY)
  }

  @Test
  def saveBytesShouldSucceedInDefaultBucketWhenBlobAlreadyInOtherOne(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID().toString())
    SMono.fromPublisher(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    SMono.fromPublisher(testee.save(defaultBucketName, blobId, SHORT_BYTEARRAY)).block()

    assertThat(SMono.fromPublisher(testee.readBytes(defaultBucketName, blobId)).block())
      .isEqualTo(SHORT_BYTEARRAY)
  }

  @Test
  def saveInputStreamShouldSucceedInDefaultBucketWhenBlobAlreadyInOtherOne(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID().toString())
    SMono.fromPublisher(testee.save(TEST_BUCKET_NAME, blobId, new ByteArrayInputStream(SHORT_BYTEARRAY))).block()

    SMono.fromPublisher(testee.save(defaultBucketName, blobId, new ByteArrayInputStream(SHORT_BYTEARRAY))).block()

    assertThat(SMono.fromPublisher(testee.readBytes(defaultBucketName, blobId)).block())
      .isEqualTo(SHORT_BYTEARRAY)
  }

  @Test
  def saveByteSourceShouldSucceedInDefaultBucketWhenBlobAlreadyInOtherOne(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID().toString())
    SMono.fromPublisher(testee.save(TEST_BUCKET_NAME, blobId, ByteSource.wrap(SHORT_BYTEARRAY))).block()

    SMono.fromPublisher(testee.save(defaultBucketName, blobId, ByteSource.wrap(SHORT_BYTEARRAY))).block()

    assertThat(SMono.fromPublisher(testee.readBytes(defaultBucketName, blobId)).block())
      .isEqualTo(SHORT_BYTEARRAY)
  }

  @Test
  def deleteShouldSuccessWithNotDefaultBucket(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID().toString())
    SMono.fromPublisher(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    assertThat(SMono.fromPublisher(testee.delete(TEST_BUCKET_NAME, blobId)).block())
  }

  @Test
  def deleteBucketShouldFailWithDefaultBucket(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID().toString())
    SMono.fromPublisher(testee.save(defaultBucketName, blobId, SHORT_BYTEARRAY)).block()

    val deleteBlobThrowingCallable: ThrowingCallable = () =>
      SMono.fromPublisher(testee.deleteBucket(defaultBucketName)).block()

    assertThatThrownBy(deleteBlobThrowingCallable)
      .isInstanceOf(classOf[ObjectStoreException])
    assertThatThrownBy(deleteBlobThrowingCallable)
      .hasMessage("Can not delete the default bucket when single save is enabled")
  }

  @Test
  def deleteBucketShouldSuccessWithNotDefaultBucket(): Unit = {
    val blobId: BlobId = blobIdFactory.of(UUID.randomUUID().toString())
    SMono.fromPublisher(testee.save(TEST_BUCKET_NAME, blobId, SHORT_BYTEARRAY)).block()

    assertThat(SMono.fromPublisher(testee.deleteBucket(TEST_BUCKET_NAME)).block())
  }
}
