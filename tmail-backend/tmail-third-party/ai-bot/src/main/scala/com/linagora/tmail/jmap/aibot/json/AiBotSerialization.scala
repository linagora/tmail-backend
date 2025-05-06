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
package com.linagora.tmail.jmap.aibot.json

import com.linagora.tmail.jmap.aibot.{AiBotSuggestReplyRequest, AiBotSuggestReplyResponse}
import play.api.libs.json._


object AiBotSerializer {

  private implicit val identityGetRequestReads: Reads[AiBotSuggestReplyRequest] = Json.reads[AiBotSuggestReplyRequest]
  private implicit val AiBotSuggestReplyResponseWrites:  OWrites[AiBotSuggestReplyResponse] = Json.writes[AiBotSuggestReplyResponse]
  private implicit val identityGetResponseReads: Reads[AiBotSuggestReplyResponse] = Json.reads[AiBotSuggestReplyResponse]

  def deserializeRequest(json: JsValue): JsResult[AiBotSuggestReplyRequest] =
    Json.fromJson[AiBotSuggestReplyRequest](json)

  def serializeResponse(response: AiBotSuggestReplyResponse): JsObject =
    Json.toJsObject(response)

}