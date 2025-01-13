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

import com.linagora.tmail.james.jmap.model.{TeamMailboxRevokeAccessRequest, TeamMailboxRevokeAccessResponse, UnparsedTeamMailbox}
import com.linagora.tmail.team.TeamMailbox
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.json.mapWrites
import play.api.libs.json.{JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes}

object TeamMailboxRevokeAccessSerializer {
  private implicit val unparsedTeamMailboxReads: Reads[UnparsedTeamMailbox] = {
    case JsString(string) => JsSuccess(UnparsedTeamMailbox(string))
    case _ => JsError("Team mailbox needs to be represented by a JsString")
  }
  private implicit val teamMailboxRevokeAccessRequestReads: Reads[TeamMailboxRevokeAccessRequest] = Json.reads[TeamMailboxRevokeAccessRequest]

  private implicit val teamMailboxWrites: Writes[TeamMailbox] = teamMailbox => JsString(teamMailbox.asString())
  private implicit val unparsedTeamMailboxMapSetErrorWrites: Writes[Map[UnparsedTeamMailbox, SetError]] =
    mapWrites[UnparsedTeamMailbox, SetError](_.value, setErrorWrites)
  private implicit val teamMailboxRevokeAccessResponseWrites: OWrites[TeamMailboxRevokeAccessResponse] = Json.writes[TeamMailboxRevokeAccessResponse]

  def deserialize(input: JsValue): JsResult[TeamMailboxRevokeAccessRequest] = Json.fromJson[TeamMailboxRevokeAccessRequest](input)
  def serialize(revokeAccessResponse: TeamMailboxRevokeAccessResponse): JsObject = Json.toJsObject(revokeAccessResponse)
}
