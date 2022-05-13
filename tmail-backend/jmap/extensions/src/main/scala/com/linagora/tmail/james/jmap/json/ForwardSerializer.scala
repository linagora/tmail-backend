package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.Forwards.FORWARD_ID
import com.linagora.tmail.james.jmap.model.{Forward, ForwardGetRequest, ForwardGetResponse, ForwardId, ForwardIds, ForwardNotFound, Forwards, LocalCopy, UnparsedForwardId}
import eu.timepit.refined
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{Properties, UuidState}
import play.api.libs.json._

class ForwardSerializer {
  private implicit val unparsedForwardIdWrites: Writes[UnparsedForwardId] = Json.valueWrites[UnparsedForwardId]
  private implicit val unparsedForwardIdReads: Reads[UnparsedForwardId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"forward id does not match Id constraints: $e"),
        id => JsSuccess(UnparsedForwardId(id)))
    case _ => JsError("forward id needs to be represented by a JsString")
  }
  private implicit val forwardIdsReads: Reads[ForwardIds] = Json.valueReads[ForwardIds]

  private implicit val forwardGetRequestReads: Reads[ForwardGetRequest] = Json.reads[ForwardGetRequest]

  private implicit val forwardIdWrites: Writes[ForwardId] = _ => JsString(FORWARD_ID.value)
  private implicit val forwardIdReads: Reads[ForwardId] = {
    case JsString("singleton") => JsSuccess(ForwardId())
    case JsString(_) => JsError("Only singleton is supported as a ForwardId")
    case _ => JsError("Expecting JsString(singleton) to represent a ForwardId")
  }

  private implicit val localCopyWrites: Writes[LocalCopy] = Json.valueWrites[LocalCopy]
  private implicit val mailAddressWrites: Writes[MailAddress] = mailAddress => JsString(mailAddress.asString)
  private implicit val forwardWrites: Writes[Forward] = Json.valueWrites[Forward]
  private implicit val forwardsWrites: Writes[Forwards] = Json.writes[Forwards]

  private implicit val forwardNotFoundWrites: Writes[ForwardNotFound] =
    notFound => JsArray(notFound.value.toList.map(id => JsString(id.id.value)))

  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]

  private implicit val forwardGetResponseWrites: Writes[ForwardGetResponse] = Json.writes[ForwardGetResponse]

  def serialize(forwardGetResponse: ForwardGetResponse)(implicit forwardsWrites: Writes[Forwards]): JsValue =
    serialize(forwardGetResponse, Forwards.allProperties)

  def serialize(forwardGetResponse: ForwardGetResponse, properties: Properties): JsValue =
    Json.toJson(forwardGetResponse)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject =>
            Forwards.propertiesFiltered(properties)
              .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get

  def deserializeForwardGetRequest(input: JsValue): JsResult[ForwardGetRequest] = Json.fromJson[ForwardGetRequest](input)
}
