package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{TeamMailboxMemberDTO, TeamMailboxMemberGetRequest, TeamMailboxMemberGetResponse, TeamMailboxMemberRoleDTO}
import org.apache.james.jmap.json.mapWrites
import play.api.libs.json.{JsResult, JsValue, Json, Reads, Writes}

object TeamMailboxMemberSerializer {
  private implicit val teamMailboxMemberGetReads: Reads[TeamMailboxMemberGetRequest] = Json.reads[TeamMailboxMemberGetRequest]

  private implicit val teamMailboxMemberRoleWrites: Writes[TeamMailboxMemberRoleDTO] = Json.writes[TeamMailboxMemberRoleDTO]
  private implicit val mapUsernameToTeamMailboxMemberRoleWrites: Writes[Map[String, TeamMailboxMemberRoleDTO]] =
    mapWrites[String, TeamMailboxMemberRoleDTO](_.toString, teamMailboxMemberRoleWrites)
  private implicit val teamMailboxMemberWrites: Writes[TeamMailboxMemberDTO] = Json.writes[TeamMailboxMemberDTO]
  private implicit val teamMailboxMemberResponseWrites: Writes[TeamMailboxMemberGetResponse] = Json.writes[TeamMailboxMemberGetResponse]

  def deserializeGetRequest(input: JsValue): JsResult[TeamMailboxMemberGetRequest] = Json.fromJson[TeamMailboxMemberGetRequest](input)
  def serializeGetResponse(response: TeamMailboxMemberGetResponse): JsValue = Json.toJson(response)
}
