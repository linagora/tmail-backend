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

package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{MailboxClearRequest, MailboxClearResponse}
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.mail.UnparsedMailboxId
import play.api.libs.json._

class MailboxClearSerializer {
  private implicit val unparsedMailboxIdReads: Reads[UnparsedMailboxId] = Json.valueReads[UnparsedMailboxId]
  private implicit val mailboxClearRequestReads: Reads[MailboxClearRequest] = Json.reads[MailboxClearRequest]

  private implicit val setErrorDescriptionWrites: Writes[SetErrorDescription] = Json.valueWrites[SetErrorDescription]
  private implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]
  private implicit val mailboxClearResponseWrites: Writes[MailboxClearResponse] = Json.writes[MailboxClearResponse]

  def serializeResponse(response: MailboxClearResponse): JsValue = Json.toJson(response)

  def deserializeRequest(input: JsValue): JsResult[MailboxClearRequest] = Json.fromJson[MailboxClearRequest](input)
}
