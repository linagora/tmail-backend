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

import com.linagora.tmail.james.jmap.model.{TeamMailboxMemberDTO, TeamMailboxMemberGetRequest, TeamMailboxMemberGetResponse, TeamMailboxMemberName, TeamMailboxMemberRoleDTO, TeamMailboxMemberSetRequest, TeamMailboxMemberSetResponse, TeamMailboxNameDTO}
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.json.mapWrites
import play.api.libs.json.{JsNull, JsResult, JsString, JsValue, Json, Reads, Writes}

object TeamMailboxMemberSerializer {
  private implicit val teamMailboxMemberGetReads: Reads[TeamMailboxMemberGetRequest] = Json.reads[TeamMailboxMemberGetRequest]

  private implicit val teamMailboxMemberRoleWrites: Writes[TeamMailboxMemberRoleDTO] = Json.writes[TeamMailboxMemberRoleDTO]
  private implicit val mapUsernameToTeamMailboxMemberRoleWrites: Writes[Map[String, TeamMailboxMemberRoleDTO]] =
    mapWrites[String, TeamMailboxMemberRoleDTO](_.toString, teamMailboxMemberRoleWrites)
  private implicit val teamMailboxMemberWrites: Writes[TeamMailboxMemberDTO] = Json.writes[TeamMailboxMemberDTO]
  private implicit val teamMailboxMemberResponseWrites: Writes[TeamMailboxMemberGetResponse] = Json.writes[TeamMailboxMemberGetResponse]

  private implicit val teamMailboxMemberRoleReads: Reads[TeamMailboxMemberRoleDTO] = Json.reads[TeamMailboxMemberRoleDTO]
  private implicit val teamMailboxMemberRoleOptionReads: Reads[Option[TeamMailboxMemberRoleDTO]] = Reads.optionWithNull[TeamMailboxMemberRoleDTO]
  private implicit val teamMailboxMemberNameReads: Reads[TeamMailboxMemberName] = Json.valueReads[TeamMailboxMemberName]
  private implicit val mapTeamMailboxMemberNameReads: Reads[Map[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]]] =
    Reads.mapReads[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]] { string => teamMailboxMemberNameReads.reads(JsString(string)) }
  private implicit val teamMailboxNameReads: Reads[TeamMailboxNameDTO] = Json.valueReads[TeamMailboxNameDTO]
  private implicit val mapTeamMailboxNameToMapReads: Reads[Map[TeamMailboxNameDTO, Map[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]]]] =
    Reads.mapReads[TeamMailboxNameDTO, Map[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]]] { string => teamMailboxNameReads.reads(JsString(string)) }
  private implicit val teamMailboxMemberSetReads: Reads[TeamMailboxMemberSetRequest] = Json.reads[TeamMailboxMemberSetRequest]

  private implicit val teamMailboxNameWrites: Writes[TeamMailboxNameDTO] = Json.valueWrites[TeamMailboxNameDTO]
  private implicit val mapTeamMailboxNameWrites: Writes[Map[TeamMailboxNameDTO, String]] =
    mapWrites[TeamMailboxNameDTO, String](teamMailboxName => teamMailboxNameWrites.writes(teamMailboxName).as[String], _ => JsNull)
  private implicit val mapTeamMailboxNameToSetErrorWrites: Writes[Map[TeamMailboxNameDTO, SetError]] =
    mapWrites[TeamMailboxNameDTO, SetError](teamMailboxName => teamMailboxName.value, setErrorWrites)
  private implicit val teamMailboxMemberSetResponseWrites: Writes[TeamMailboxMemberSetResponse] = Json.writes[TeamMailboxMemberSetResponse]

  def deserializeGetRequest(input: JsValue): JsResult[TeamMailboxMemberGetRequest] = Json.fromJson[TeamMailboxMemberGetRequest](input)
  def serializeGetResponse(response: TeamMailboxMemberGetResponse): JsValue = Json.toJson(response)

  def deserializeSetRequest(input: JsValue): JsResult[TeamMailboxMemberSetRequest] = Json.fromJson[TeamMailboxMemberSetRequest](input)
  def serializeSetResponse(response: TeamMailboxMemberSetResponse): JsValue = Json.toJson(response)
}
