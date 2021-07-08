package com.linagora.tmail.encrypted

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import com.google.common.io.ByteSource
import com.linagora.tmail.encrypted.EncryptedAttachmentBlobId.prefix
import com.linagora.tmail.pgp.Encrypter
import org.apache.james.jmap.api.model.Preview
import org.apache.james.mailbox.model.{MessageId, ParsedAttachment}

import scala.jdk.OptionConverters._
import scala.util.Try

class EncryptedEmailContentFactory(encrypter: Encrypter) {

  def encrypt(clearEmailContent: ClearEmailContent, messageId: MessageId): EncryptedEmailContent =
    EncryptedEmailContent(encryptedPreview = encryptPreview(clearEmailContent.preview),
      encryptedHtml = encrypt(clearEmailContent.html),
      hasAttachment = clearEmailContent.hasAttachment,
      encryptedAttachmentMetadata = encryptAttachmentMetadata(clearEmailContent.attachments, messageId),
      encryptedAttachmentContents = encryptAttachmentContent(clearEmailContent.attachments))

  private def encryptPreview(preview: Preview): String =
    encrypt(preview.getValue)

  private def encryptAttachmentMetadata(parsedAttachments: List[ParsedAttachment], messageId: MessageId): Option[String] = {
    parsedAttachments match {
      case Nil | List() => None
      case _ =>
        val position: AtomicInteger = new AtomicInteger(0)
        Some(encrypt(AttachmentMetaDataSerializer
          .serializeList(parsedAttachments
            .map(parsedAttachment => AttachmentMetadata.fromJava(parsedAttachment, position.getAndIncrement(), messageId)))
          .toString()))
    }
  }

  private def encryptAttachmentContent(parsedAttachments: List[ParsedAttachment]): List[String] =
    parsedAttachments match {
      case Nil | List() => List.empty
      case _ => parsedAttachments.map(parsedAttachment => encrypt(parsedAttachment.getContent))
    }

  private def encrypt(byteSource: ByteSource): String = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream
    encrypter.encrypt(byteSource, stream)
    new String(stream.toByteArray, StandardCharsets.UTF_8)
  }

  private def encrypt(value: String): String =
    encrypt(ByteSource.wrap(value.getBytes(StandardCharsets.UTF_8)))
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
      blobId = EncryptedAttachmentBlobId(messageId, position).serialize,
      name = parsedAttachment.getName.toScala,
      contentType = parsedAttachment.getContentType.asString(),
      cid = parsedAttachment.getCid
        .toScala
        .map(cid => cid.getValue),
      isLine = parsedAttachment.isInline,
      size = parsedAttachment.getContent.size())
}

object EncryptedAttachmentBlobId {
  private val prefix: String = "encryptedAttachment_"

  def parse(messageIdFactory: MessageId.Factory, string: String): Either[IllegalArgumentException, EncryptedAttachmentBlobId] =
    if (string.startsWith(prefix)) {
      val positionIndex = string.lastIndexOf('_')

      val aTry: Try[EncryptedAttachmentBlobId] = for {
        position <- Try(string.substring(positionIndex + 1).toInt)
          .filter(i => i >= 0)
        messageId <- Try(messageIdFactory.fromString(string.substring(prefix.length + 1, positionIndex)))
      } yield {
        EncryptedAttachmentBlobId(messageId, position)
      }
      aTry.toEither
        .left.map(new IllegalArgumentException(_))
    } else {
      Left(new IllegalArgumentException(s"Must start with $prefix"))
    }
}

case class EncryptedAttachmentBlobId(messageId: MessageId, position: Int) {
  def serialize: String = {
    s"$prefix${messageId.serialize()}_$position"
  }
}

case class AttachmentMetadata(position: Int,
                              blobId: String,
                              name: Option[String],
                              contentType: String,
                              cid: Option[String],
                              isLine: Boolean,
                              size: Long)
