package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.method.JSON_CUSTOM_VALIDATION_ERROR
import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelCreationId, LabelCreationParseException, LabelCreationRequest, LabelCreationResponse, LabelGetRequest, LabelGetResponse, LabelId, LabelIds, LabelPatchObject, LabelSetRequest, LabelSetResponse, LabelUpdateResponse, UnparsedLabelId}
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError, UuidState}
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.Keyword
import play.api.libs.json.{Format, JsArray, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, JsonValidationError, Reads, Writes, __}

object LabelSerializer {
  private implicit val labelIdFormat: Format[LabelId] = Json.valueFormat[LabelId]
  private implicit val unparsedLabelIdReads: Reads[UnparsedLabelId] = Json.valueReads[UnparsedLabelId]
  private implicit val unparsedLabelIdWrites: Writes[UnparsedLabelId] = Json.valueWrites[UnparsedLabelId]

  private implicit val labelIdsReads: Reads[LabelIds] = Json.valueReads[LabelIds]
  private implicit val labelGetRequestReads: Reads[LabelGetRequest] = Json.reads[LabelGetRequest]

  private implicit val labelCreationIdFormat: Format[LabelCreationId] = Json.valueFormat[LabelCreationId]

  private implicit val colorWrites: Writes[Color] = Json.valueWrites[Color]
  private implicit val colorReads: Reads[Color] = {
    case jsString: JsString => Color.validate(jsString.value)
      .fold(e => JsError(e.getMessage),
        color => JsSuccess(color))
    case _ => JsError("Expecting a string as a Color")
  }

  private implicit val keywordWrites: Writes[Keyword] = Json.valueWrites[Keyword]
  private implicit val displayNameFormat: Format[DisplayName] = Json.valueFormat[DisplayName]
  private implicit val labelWrites: Writes[Label] = Json.writes[Label]
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val labelGetResponseWrites: Writes[LabelGetResponse] = Json.writes[LabelGetResponse]

  private implicit val mapCreationRequestByLabelCreationId: Reads[Map[LabelCreationId, JsObject]] =
    Reads.mapReads[LabelCreationId, JsObject] { string => Json.valueReads[LabelCreationId].reads(JsString(string)) }

  implicit val labelCreationResponseWrites: Writes[LabelCreationResponse] = Json.writes[LabelCreationResponse]

  private implicit val labelMapSetErrorForCreationWrites: Writes[Map[LabelCreationId, SetError]] =
    mapWrites[LabelCreationId, SetError](_.id.value, setErrorWrites)

  private implicit val labelMapCreationResponseWrites: Writes[Map[LabelCreationId, LabelCreationResponse]] =
    mapWrites[LabelCreationId, LabelCreationResponse](_.id.value, labelCreationResponseWrites)

  private implicit val labelUpdateResponseWrites: Writes[LabelUpdateResponse] = Json.valueWrites[LabelUpdateResponse]

  private implicit val patchObject: Reads[LabelPatchObject] = Json.valueReads[LabelPatchObject]

  private implicit val labelMapUpdateRequestReads: Reads[Map[UnparsedLabelId, LabelPatchObject]] =
    Reads.mapReads[UnparsedLabelId, LabelPatchObject] { string => unparsedLabelIdReads.reads(JsString(string)) }

  implicit val labelCreationRequestStandardWrites: Writes[LabelCreationRequest] = Json.writes[LabelCreationRequest]

  private val labelCreationRequestStandardReads: Reads[LabelCreationRequest] = Json.reads[LabelCreationRequest]

  implicit val labelCreationRequestReads: Reads[LabelCreationRequest] = new Reads[LabelCreationRequest] {
    override def reads(json: JsValue): JsResult[LabelCreationRequest] = {
      validateProperties(json.as[JsObject])
        .fold(exception => JsError(JsonValidationError(JSON_CUSTOM_VALIDATION_ERROR, exception.setError)),
          _ => labelCreationRequestStandardReads.reads(json))
    }

    def validateProperties(jsObject: JsObject): Either[LabelCreationParseException, JsObject] =
      (jsObject.keys.intersect(LabelCreationRequest.serverSetProperty),
        jsObject.keys.diff(LabelCreationRequest.knownProperties)) match {
        case (_, unknownProperties) if unknownProperties.nonEmpty =>
          Left(LabelCreationParseException(SetError.invalidArguments(
            SetErrorDescription("Some unknown properties were specified"),
            Some(toProperties(unknownProperties.toSet)))))
        case (specifiedServerSetProperties, _) if specifiedServerSetProperties.nonEmpty =>
          Left(LabelCreationParseException(SetError.invalidArguments(
            SetErrorDescription("Some server-set properties were specified"),
            Some(toProperties(specifiedServerSetProperties.toSet)))))
        case _ => scala.Right(jsObject)
      }

    private def toProperties(strings: Set[String]): Properties = Properties(strings
      .flatMap(string => {
        val refinedValue: Either[String, NonEmptyString] = refineV[NonEmpty](string)
        refinedValue.fold(_ => None, Some(_))
      }))
  }

  def deserializeLabelCreationRequest(input: JsValue): JsResult[LabelCreationRequest] = Json.fromJson[LabelCreationRequest](input)

  private implicit val labelMapUpdateResponseWrites: Writes[Map[LabelId, LabelUpdateResponse]] =
    mapWrites[LabelId, LabelUpdateResponse](_.id.value, labelUpdateResponseWrites)

  private implicit val mapSetErrorForUpdateWrites: Writes[Map[UnparsedLabelId, SetError]] =
    mapWrites[UnparsedLabelId, SetError](_.id.value, setErrorWrites)

  private implicit val labelSetResponseWrites: Writes[LabelSetResponse] = Json.writes[LabelSetResponse]

  private implicit val labelSetRequestReads: Reads[LabelSetRequest] = Json.reads[LabelSetRequest]

  def deserializeGetRequest(input: JsValue): JsResult[LabelGetRequest] = Json.fromJson[LabelGetRequest](input)

  def serializeGetResponse(response: LabelGetResponse, properties: Properties): JsValue =
    Json.toJson(response)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject => properties
            .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get

  def deserializeLabelSetRequest(input: JsValue): JsResult[LabelSetRequest] = Json.fromJson[LabelSetRequest](input)

  def serializeLabelSetResponse(response: LabelSetResponse): JsValue = Json.toJson(response)
}
