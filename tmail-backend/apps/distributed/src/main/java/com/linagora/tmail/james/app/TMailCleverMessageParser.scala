package com.linagora.tmail.james.app

import com.google.common.io.ByteSource
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream
import org.apache.james.jmap.mail.{BlobId, Disposition, EmailBodyPart}
import org.apache.james.jmap.method.ZoneIdProvider
import org.apache.james.mailbox.model.{Cid, MessageId, ParsedAttachment}
import org.apache.james.mailbox.store.mail.model.impl.{FileBufferedBodyFactory, MessageParser}
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.dom.{Message, SingleBody}
import org.apache.james.mime4j.message.{DefaultMessageBuilder, DefaultMessageWriter}
import org.apache.james.mime4j.stream.MimeConfig

import java.io.InputStream
import java.util
import scala.jdk.OptionConverters._
import scala.jdk.CollectionConverters._


case class EmailBodyPartContent(part: EmailBodyPart) extends ByteSource {
  override def size: Long = part.size.value

  override def openStream: InputStream = part.entity.getBody match {
    case body: SingleBody => body.getInputStream
    case body =>
      val writer = new DefaultMessageWriter
      val outputStream = new UnsynchronizedByteArrayOutputStream()
      writer.writeBody(body, outputStream)
      outputStream.toInputStream
  }
}

case class TMailCleverParsedAttachment(parsedAttachment: ParsedAttachment, blobId: BlobId) extends ParsedAttachment(parsedAttachment.getContentType,
  parsedAttachment.getContent, parsedAttachment.getName, parsedAttachment.getCid, parsedAttachment.isInline) {

  def translate(messageId: MessageId): String = {
    val rawValue = blobId.value.value
    if (rawValue.startsWith(TMailCleverParsedAttachment.placeholder)) {
      messageId.serialize() + rawValue.substring(TMailCleverParsedAttachment.placeholder.length)
    }
    throw new RuntimeException("unsuported")
  }
}

object TMailCleverParsedAttachment {
  val placeholder: String = "___PLACEHOLDER___"
}

class TMailCleverMessageParser(zoneIdProvider: ZoneIdProvider) extends MessageParser {
  override def retrieveAttachments(fullContent: InputStream): MessageParser.ParsingResult = {
    val defaultMessageBuilder = new DefaultMessageBuilder
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT)
    val bodyFactory = new FileBufferedBodyFactory
    defaultMessageBuilder.setBodyFactory(bodyFactory)
    try {
      val message = defaultMessageBuilder.parseMessage(fullContent)
      new MessageParser.ParsingResult(retrieveAttachments(message), () => bodyFactory.dispose())
    } catch {
      case e: Exception =>
        // Release associated temporary files
        bodyFactory.dispose()
        throw e
    }
  }

  override def retrieveAttachments(message: Message): util.List[ParsedAttachment] = {
    val value = BlobId.of(TMailCleverParsedAttachment.placeholder).get
    val triedPart = EmailBodyPart.of(None, zoneIdProvider.get(), value, message)
    triedPart.map(part => part.attachments).toOption.getOrElse(List())
      .map(attachment => {
        val isInline = attachment.disposition.contains(Disposition.INLINE) && attachment.cid.isDefined
        TMailCleverParsedAttachment(ParsedAttachment.builder
          .contentType(attachment.`type`.value)
          .content(EmailBodyPartContent(attachment))
          .name(attachment.name.map(_.value).toJava)
          .cid(attachment.cid.map(_.getValue).map(Cid.from).toJava)
          .inline(isInline), attachment.blobId.get)
          .asInstanceOf[ParsedAttachment]
      }).asJava
  }
}
