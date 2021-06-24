package com.linagora.tmail.encrypted

import com.linagora.tmail.encrypted.EncryptedEmailContentStoreContract.{ENCRYPTED_EMAIL_CONTENT, ENCRYPTED_EMAIL_CONTENT_NO_ATTACHMENT, POSITION_NUMBER_START_AT}
import org.apache.james.blob.api.{BlobId, BlobStore, BucketName, ObjectStoreException}
import org.apache.james.mailbox.model.MessageId
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

object EncryptedEmailContentStoreContract {
  private lazy val ENCRYPTED_EMAIL_CONTENT_NO_ATTACHMENT: EncryptedEmailContent = EncryptedEmailContent(
    encryptedPreview = "encryptedPreview1",
    encryptedHtml = "encryptedHtml1",
    hasAttachment = false,
    encryptedAttachmentMetadata = None,
    encryptedAttachmentContents = List())

  private lazy val ENCRYPTED_EMAIL_CONTENT: EncryptedEmailContent = EncryptedEmailContent(
    encryptedPreview = "encryptedPreview1",
    encryptedHtml = "encryptedHtml1",
    hasAttachment = true,
    encryptedAttachmentMetadata = Some("encryptedAttachmentMetadata1"),
    encryptedAttachmentContents = List("encryptedAttachmentContents1"))

  private lazy val POSITION_NUMBER_START_AT: Int = 0
}

trait EncryptedEmailContentStoreContract {
  def testee: EncryptedEmailContentStore

  def randomMessageId: MessageId

  def blobStore: BlobStore

  def bucketName: BucketName

  @Test
  def saveShouldSuccessWhenNoAttachment(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT_NO_ATTACHMENT)).block()
    assertThat(SMono.fromPublisher(testee.retrieveFastView(messageId)).block())
      .isNotNull
  }

  @Test
  def saveShouldSuccessWhenHasAttachment(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT)).block()
    assertThat(SMono.fromPublisher(testee.retrieveFastView(messageId)).block())
      .isNotNull
  }

  @Test
  def saveShouldStoreBlobWhenHasAttachment(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT)).block()
    val blobId: BlobId = SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, POSITION_NUMBER_START_AT)).block()
    assertThat(blobStore.read(bucketName, blobId).readAllBytes())
      .isNotNull
  }

  @Test
  def retrieveFastViewShouldSuccessWhenNoAttachment(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT_NO_ATTACHMENT)).block()
    assertThat(SMono.fromPublisher(testee.retrieveFastView(messageId)).block())
      .isEqualTo(EncryptedEmailFastView(
        encryptedPreview = EncryptedPreview(ENCRYPTED_EMAIL_CONTENT_NO_ATTACHMENT.encryptedPreview),
        hasAttachment = ENCRYPTED_EMAIL_CONTENT_NO_ATTACHMENT.hasAttachment))
  }

  @Test
  def retrieveFastViewShouldSuccessWhenHasAttachment(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT)).block()
    assertThat(SMono.fromPublisher(testee.retrieveFastView(messageId)).block())
      .isEqualTo(EncryptedEmailFastView(
        encryptedPreview = EncryptedPreview(ENCRYPTED_EMAIL_CONTENT.encryptedPreview),
        hasAttachment = ENCRYPTED_EMAIL_CONTENT.hasAttachment))
  }

  @Test
  def retrieveFastViewShouldThrowWhenMessageIdNotFound(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.retrieveFastView(randomMessageId)).block())
      .isInstanceOf(classOf[MessageNotFoundException])
  }

  @Test
  def retrieveDetailedViewShouldSuccessWhenNoAttachment(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT_NO_ATTACHMENT)).block()
    assertThat(SMono.fromPublisher(testee.retrieveDetailedView(messageId)).block())
      .isEqualTo(EncryptedEmailDetailedView(
        encryptedPreview = EncryptedPreview(ENCRYPTED_EMAIL_CONTENT_NO_ATTACHMENT.encryptedPreview),
        encryptedHtml = EncryptedHtml(ENCRYPTED_EMAIL_CONTENT_NO_ATTACHMENT.encryptedHtml),
        hasAttachment = ENCRYPTED_EMAIL_CONTENT_NO_ATTACHMENT.hasAttachment,
        encryptedAttachmentMetadata = None))
  }

  @Test
  def retrieveDetailedViewShouldSuccessWhenHasAttachment(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT)).block()
    assertThat(SMono.fromPublisher(testee.retrieveDetailedView(messageId)).block())
      .isEqualTo(EncryptedEmailDetailedView(
        encryptedPreview = EncryptedPreview(ENCRYPTED_EMAIL_CONTENT.encryptedPreview),
        encryptedHtml = EncryptedHtml(ENCRYPTED_EMAIL_CONTENT.encryptedHtml),
        hasAttachment = ENCRYPTED_EMAIL_CONTENT.hasAttachment,
        encryptedAttachmentMetadata = Some(EncryptedAttachmentMetadata(ENCRYPTED_EMAIL_CONTENT.encryptedAttachmentMetadata.get))))
  }

  @Test
  def retrieveDetailedViewShouldThrowWhenMessageIdNotFound(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.retrieveDetailedView(randomMessageId)).block())
      .isInstanceOf(classOf[MessageNotFoundException])
  }

  @Test
  def retrieveAttachmentContentShouldSuccessWhenHasAttachment(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT)).block()
    assertThat(SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, POSITION_NUMBER_START_AT)).block())
      .isNotNull
  }

  @Test
  def retrieveAttachmentContentShouldThrowWhenPositionNotFound(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT)).block()
    assertThatThrownBy(() => SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, 10)).block())
      .isInstanceOf(classOf[AttachmentNotFoundException])
  }

  @Test
  def retrieveAttachmentContentShouldThrowWhenMessageIdNotFound(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.retrieveAttachmentContent(randomMessageId, POSITION_NUMBER_START_AT)).block())
      .isInstanceOf(classOf[AttachmentNotFoundException])
  }

  @Test
  def storeShouldSuccessWhenMultiAttachments(): Unit = {
    val emailContent: EncryptedEmailContent = EncryptedEmailContent(
      encryptedPreview = "encryptedPreview1",
      encryptedHtml = "encryptedHtml1",
      hasAttachment = true,
      encryptedAttachmentMetadata = Some("encryptedAttachmentMetadata1"),
      encryptedAttachmentContents = List("encryptedAttachmentContents1",
        "encryptedAttachmentContents2",
        "encryptedAttachmentContents3"))

    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, emailContent)).block()
    assertThat(SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, 0)).block())
      .isNotNull
    assertThat(SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, 1)).block())
      .isNotNull
    assertThat(SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, 2)).block())
      .isNotNull
  }

  @Test
  def attachmentShouldStore(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT)).block()
    val blobId: BlobId = SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, POSITION_NUMBER_START_AT)).block()
    assertThat(blobStore.read(bucketName, blobId))
      .hasSameContentAs(new ByteArrayInputStream(ENCRYPTED_EMAIL_CONTENT.encryptedAttachmentContents
        .head
        .getBytes(StandardCharsets.UTF_8)))
  }

  @Test
  def attachmentPositionShouldStartAtZero(): Unit = {
    val messageId: MessageId = randomMessageId
    val encryptedEmailContent: EncryptedEmailContent = EncryptedEmailContent(
      encryptedPreview = "encryptedPreview1",
      encryptedHtml = "encryptedHtml1",
      hasAttachment = true,
      encryptedAttachmentMetadata = Some("encryptedAttachmentMetadata1"),
      encryptedAttachmentContents = List("encryptedAttachmentContents1"))

    SMono.fromPublisher(testee.store(messageId, encryptedEmailContent)).block()

    assertThat(SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, 0)).block())
      .isNotNull

    assertThatThrownBy(() => SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, 1)).block())
      .isInstanceOf(classOf[AttachmentNotFoundException])
  }

  @Test
  def attachmentPositionShouldPreserveOrdering(): Unit = {
    val messageId: MessageId = randomMessageId
    val encryptedEmailContent: EncryptedEmailContent = EncryptedEmailContent(
      encryptedPreview = "encryptedPreview1",
      encryptedHtml = "encryptedHtml1",
      hasAttachment = true,
      encryptedAttachmentMetadata = Some("encryptedAttachmentMetadata1"),
      encryptedAttachmentContents = List("encryptedAttachmentContents1", "encryptedAttachmentContents2", "encryptedAttachmentContents3"))

    SMono.fromPublisher(testee.store(messageId, encryptedEmailContent)).block()
    val blobId0: BlobId = SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, 0)).block()
    val blobId1: BlobId = SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, 1)).block()
    val blobId2: BlobId = SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, 2)).block()

    assertThat(List(
      new String(SMono.fromPublisher(blobStore.readBytes(bucketName, blobId0)).block(), StandardCharsets.UTF_8),
      new String(SMono.fromPublisher(blobStore.readBytes(bucketName, blobId1)).block(), StandardCharsets.UTF_8),
      new String(SMono.fromPublisher(blobStore.readBytes(bucketName, blobId2)).block(), StandardCharsets.UTF_8)))
      .isEqualTo(encryptedEmailContent.encryptedAttachmentContents)
  }

  @Test
  def deleteShouldSuccess(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT)).block()
    SMono.fromPublisher(testee.delete(messageId)).block()
    assertThatThrownBy(() => SMono.fromPublisher(testee.retrieveFastView(messageId)).block())
      .isInstanceOf(classOf[MessageNotFoundException])
  }

  @Test
  def deleteShouldNotThrowWhenMessageIdNotFound(): Unit = {
    assertThatCode(() => testee.delete(randomMessageId))
      .doesNotThrowAnyException()
  }

  @Test
  def deleteShouldRemoveAttachmentInBlobStore(): Unit = {
    val messageId: MessageId = randomMessageId
    SMono.fromPublisher(testee.store(messageId, ENCRYPTED_EMAIL_CONTENT)).block()
    val blobId: BlobId = SMono.fromPublisher(testee.retrieveAttachmentContent(messageId, POSITION_NUMBER_START_AT)).block()
    SMono.fromPublisher(testee.delete(messageId)).block()

    assertThatThrownBy(() => blobStore.read(bucketName, blobId).read())
      .isInstanceOf(classOf[ObjectStoreException])
  }
}
