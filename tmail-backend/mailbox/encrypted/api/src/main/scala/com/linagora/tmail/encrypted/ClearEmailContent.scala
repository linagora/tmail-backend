/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.encrypted

import jakarta.inject.Inject
import org.apache.james.jmap.api.model.Preview
import org.apache.james.mailbox.model.ParsedAttachment
import org.apache.james.mailbox.store.mail.model.impl.MessageParser
import org.apache.james.mime4j.dom.Message
import org.apache.james.util.mime.MessageContentExtractor

import scala.jdk.CollectionConverters._
import scala.util.Try

class ClearEmailContentFactory @Inject()(messageParser: MessageParser,
                                         messageContentExtractor: MessageContentExtractor,
                                         previewFactory: Preview.Factory) {

  def from(message: Message): Try[ClearEmailContent] = {
    for {
      attachments <- Try(messageParser.retrieveAttachments(message).asScala.toList)
      preview <- Try(previewFactory.fromMime4JMessage(message))
      messageContent <- Try(messageContentExtractor.extract(message))
      html = messageContent.getHtmlBody.or(() => messageContent.getTextBody).orElse("")
    } yield {
      ClearEmailContent(
        preview = preview,
        hasAttachment = attachments.nonEmpty,
        html = html,
        attachments = attachments)
    }
  }
}

case class ClearEmailContent(preview: Preview,
                             hasAttachment: Boolean,
                             html: String,
                             attachments: List[ParsedAttachment])
