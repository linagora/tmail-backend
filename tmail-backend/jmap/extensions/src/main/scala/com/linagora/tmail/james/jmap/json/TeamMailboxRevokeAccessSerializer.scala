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
