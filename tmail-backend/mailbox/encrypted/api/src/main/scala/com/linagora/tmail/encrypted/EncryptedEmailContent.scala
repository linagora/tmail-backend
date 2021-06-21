package com.linagora.tmail.encrypted

import com.google.common.io.ByteSource
import com.linagora.tmail.pgp.Encrypter
import org.apache.james.jmap.api.model.Preview
import org.apache.james.mailbox.model.{MessageId, ParsedAttachment}

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.OptionConverters._

class EncryptedEmailContentFactory(encrypter: Encrypter) {

  def encrypt(clearEmailContent: ClearEmailContent, messageId: MessageId): EncryptedEmailContent =
    EncryptedEmailContent(encryptedPreview = encryptPreview(clearEmailContent.preview),
      encryptedHtml = encrypt(clearEmailContent.html),
      hasAttachment = clearEmailContent.hasAttachment,
      encryptedAttachmentMetadata = encryptAttachmentMetadata(clearEmailContent.attachments, messageId),
      encryptedAttachmentContents = encryptAttachmentContent(clearEmailContent.attachments))

  def encryptPreview(preview: Preview): String =
    encrypt(preview.getValue)

  def encryptAttachmentMetadata(parsedAttachments: List[ParsedAttachment], messageId: MessageId): Option[String] = {
    if (parsedAttachments.isEmpty) return None
    Some(encrypt(AttachmentMetaDataSerializer
      .serializeList(AttachmentMetadata.fromJava(parsedAttachments, messageId))
      .toString()))
  }

  def encryptAttachmentContent(parsedAttachments: List[ParsedAttachment]): List[String] = {
    if (parsedAttachments.isEmpty) return List.empty
    parsedAttachments.map(parsedAttachment => encrypt(parsedAttachment.getContent))
  }

  def encrypt(byteSource: ByteSource): String = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream
    encrypter.encrypt(byteSource, stream)
    new String(stream.toByteArray, StandardCharsets.UTF_8)
  }

  def encrypt(value: String): String = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream
    encrypter.encrypt(ByteSource.wrap(value.getBytes(StandardCharsets.UTF_8)), stream)
    new String(stream.toByteArray, StandardCharsets.UTF_8)
  }
}

case class EncryptedEmailContent(encryptedPreview: String,
                                 encryptedHtml: String,
                                 hasAttachment: Boolean,
                                 encryptedAttachmentMetadata: Option[String],
                                 encryptedAttachmentContents: List[String])

object AttachmentMetadata {
  def fromJava(parsedAttachment: ParsedAttachment, position: Int, messageId: MessageId): AttachmentMetadata =
    AttachmentMetadata(
      position = position,
      blobId = s"encryptedAttachment_${messageId.serialize()}_$position",
      name = parsedAttachment.getName.toScala,
      contentType = parsedAttachment.getContentType.asString(),
      cid = parsedAttachment.getCid
        .toScala
        .map(cid => cid.getValue),
      isLine = parsedAttachment.isInline,
      size = parsedAttachment.getContent.size())

  def fromJava(listParsedAttachment: List[ParsedAttachment], messageId: MessageId): List[AttachmentMetadata] = {
    val position: AtomicInteger = new AtomicInteger(0)
    listParsedAttachment
      .map(parsedAttachment => fromJava(parsedAttachment, position.getAndIncrement(), messageId))
  }
}

case class AttachmentMetadata(position: Int,
                              blobId: String,
                              name: Option[String],
                              contentType: String,
                              cid: Option[String],
                              isLine: Boolean,
                              size: Long)
