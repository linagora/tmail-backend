package com.linagora.tmail.encrypted

import org.apache.james.blob.api.BlobId
import org.apache.james.mailbox.model.MessageId
import org.reactivestreams.Publisher

object EncryptedEmailContentStore {
  val POSITION_NUMBER_START_AT: Int = 0
}

trait EncryptedEmailContentStore {
  def store(messageId: MessageId, encryptedEmailContent: EncryptedEmailContent): Publisher[Unit]

  def delete(messageId: MessageId): Publisher[Unit]

  def retrieveFastView(messageId: MessageId): Publisher[EncryptedEmailFastView]

  def retrieveDetailedView(messageId: MessageId): Publisher[EncryptedEmailDetailedView]

  def retrieveAttachmentContent(messageId: MessageId, position: Int): Publisher[BlobId]
}

case class MessageNotFoundException(messageId: MessageId) extends RuntimeException

case class AttachmentNotFoundException(messageId: MessageId, position: Int) extends RuntimeException

object EncryptedEmailFastView {
  def from(encryptedEmailContent: EncryptedEmailContent): EncryptedEmailFastView =
    EncryptedEmailFastView(
      encryptedPreview = EncryptedPreview(encryptedEmailContent.encryptedPreview),
      hasAttachment = encryptedEmailContent.hasAttachment)

  def from(encryptedEmailDetailedView: EncryptedEmailDetailedView): EncryptedEmailFastView =
    EncryptedEmailFastView(
      encryptedPreview = encryptedEmailDetailedView.encryptedPreview,
      hasAttachment = encryptedEmailDetailedView.hasAttachment)
}

case class EncryptedEmailFastView(encryptedPreview: EncryptedPreview,
                                  hasAttachment: Boolean)

object EncryptedEmailDetailedView {
  def from(encryptedEmailContent: EncryptedEmailContent): EncryptedEmailDetailedView =
    EncryptedEmailDetailedView(
      encryptedPreview = EncryptedPreview(encryptedEmailContent.encryptedPreview),
      encryptedHtml = EncryptedHtml(encryptedEmailContent.encryptedHtml),
      hasAttachment = encryptedEmailContent.hasAttachment,
      encryptedAttachmentMetadata = encryptedEmailContent.encryptedAttachmentMetadata
        .map(value => EncryptedAttachmentMetadata(value)))
}

case class EncryptedAttachmentMetadata(value: String) extends AnyVal
case class EncryptedPreview(value: String) extends AnyVal
case class EncryptedHtml(value: String) extends AnyVal

case class EncryptedEmailDetailedView(encryptedPreview: EncryptedPreview,
                                      encryptedHtml: EncryptedHtml,
                                      hasAttachment: Boolean,
                                      encryptedAttachmentMetadata: Option[EncryptedAttachmentMetadata])

