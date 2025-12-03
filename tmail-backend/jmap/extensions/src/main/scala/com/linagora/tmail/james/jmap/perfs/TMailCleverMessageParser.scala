/** ******************************************************************
 * As a subpart of Twake Mail, this file is edited by Linagora.    *
 * *
 * https://twake-mail.com/                                         *
 * https://linagora.com                                            *
 * *
 * This file is subject to The Affero Gnu Public License           *
 * version 3.                                                      *
 * *
 * https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 * *
 * This program is distributed in the hope that it will be         *
 * useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 * PURPOSE. See the GNU Affero General Public License for          *
 * more details.                                                   *
 * ****************************************************************** */

package com.linagora.tmail.james.jmap.perfs

import java.io.InputStream
import java.util
import java.util.Locale

import com.google.common.io.ByteSource
import jakarta.inject.Inject
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream
import org.apache.james.jmap.mail.{BlobId, Disposition, EmailBodyPart}
import org.apache.james.jmap.method.ZoneIdProvider
import org.apache.james.mailbox.model.{Cid, MessageId, ParsedAttachment}
import org.apache.james.mailbox.store.mail.model.impl.{FileBufferedBodyFactory, MessageParser}
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.dom.{Message, SingleBody}
import org.apache.james.mime4j.message.{DefaultMessageBuilder, DefaultMessageWriter}
import org.apache.james.mime4j.stream.MimeConfig

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._


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
    } else {
      throw new RuntimeException("unsuported blobid value " + rawValue)
    }
  }
}

object TMailCleverParsedAttachment {
  val placeholder: String = "___PLACEHOLDER___"
}

class TMailCleverMessageParser @Inject() (zoneIdProvider: ZoneIdProvider) extends MessageParser {
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
          .contentType(attachment.`type`.value + attachment.charset.map(charset => "; charset=" + charset.value.toUpperCase(Locale.US)).getOrElse(""))
          .content(EmailBodyPartContent(attachment))
          .name(attachment.name.map(_.value).toJava)
          .cid(attachment.cid.map(_.getValue).map(Cid.from).toJava)
          .inline(isInline), attachment.blobId.get)
          .asInstanceOf[ParsedAttachment]
      }).asJava
  }
}
