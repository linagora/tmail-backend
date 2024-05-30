package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.method.standardErrorMessage
import com.linagora.tmail.james.jmap.model.{PublicAssetDTO, PublicAssetGetRequest, PublicAssetGetResponse}
import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import com.linagora.tmail.james.jmap.publicAsset.{PublicAssetCreationId, PublicAssetCreationResponse, PublicAssetId, PublicAssetPatchObject, PublicAssetSetCreationRequest, PublicAssetSetRequest, PublicAssetSetResponse, PublicAssetUpdateResponse, PublicURI, UnparsedPublicAssetId, ValidatedPublicAssetPatchObject}
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.core.{SetError, UuidState}
import org.apache.james.jmap.json.{mapMarkerReads, mapWrites}
import org.apache.james.jmap.mail.{IdentityIds, UnparsedIdentityId, BlobId => JmapBlobId}
import play.api.libs.json.{JsBoolean, JsError, JsNull, JsObject, JsPath, JsResult, JsString, JsSuccess, JsValue, Json, JsonValidationError, Reads, Writes}

object PublicAssetSerializer {

  object PublicAssetSetUpdateReads {

    def reads(jsObject: JsObject): JsResult[ValidatedPublicAssetPatchObject] =
      asValidatedPublicAssetPatchObject(jsObject.value.map {
        case (property, value) => UpdateRequestValidation.from(property, value)
      }.toSeq)

    private def asValidatedPublicAssetPatchObject(entries: Seq[UpdateRequestValidation]): JsResult[ValidatedPublicAssetPatchObject] = {
      entries.flatMap(_.asJsError)
        .headOption
        .getOrElse({
          val identityIdsReset: Option[Seq[IdentityId]] = entries.flatMap {
            case reset: IdentityIdsReset => Some(reset.identityIds)
            case _ => None
          }.headOption

          val identityIdsToAdd: Seq[IdentityId] = entries.flatMap {
            case addition: IdentityIdAddition => Some(addition.identityId)
            case _ => None
          }

          val identityIdsToRemove: Seq[IdentityId] = entries.flatMap {
            case removal: IdentityIdRemoval => Some(removal.identityId)
            case _ => None
          }
          JsSuccess(ValidatedPublicAssetPatchObject(identityIdsReset, identityIdsToAdd, identityIdsToRemove))
        })
    }

    sealed trait UpdateRequestValidation {
      def asJsError: Option[JsError] = None
    }

    private object InvalidPatchEntryValue {
      def from(property: String, errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): InvalidPatchEntryValue =
        InvalidPatchEntryValue(property, standardErrorMessage(errors))
    }

    private case class InvalidPatchEntryValue(property: String, cause: String) extends UpdateRequestValidation {
      override def asJsError: Option[JsError] = Some(JsError.apply(JsPath \ property, cause))
    }

    private case class IdentityIdsReset(identityIds: Seq[IdentityId]) extends UpdateRequestValidation

    private case class IdentityIdAddition(identityId: IdentityId) extends UpdateRequestValidation

    private case class IdentityIdRemoval(identityId: IdentityId) extends UpdateRequestValidation

    private object UpdateRequestValidation {
      private val identityIdsPrefix: String = PublicAssetPatchObject.identityIdsProperty + "/"

      def from(property: String, value: JsValue): UpdateRequestValidation =
        property match {
          case PublicAssetPatchObject.identityIdsProperty => identityIdSeqReads.reads(value)
            .fold(e => InvalidPatchEntryValue.from(property, e), IdentityIdsReset)
          case name if name.startsWith(identityIdsPrefix) => PublicAssetPatchObject.validateIdentityId(name.substring(identityIdsPrefix.length))
            .fold(e => InvalidPatchEntryValue(property, e.toString), identityId => value match {
              case JsBoolean(true) => IdentityIdAddition(identityId)
              case JsNull => IdentityIdRemoval(identityId)
              case _ => InvalidPatchEntryValue(property, "Value must be a true or null")
            })
          case _ => InvalidPatchEntryValue(property, "Unknown property")
        }
    }
  }

  private implicit val identityIdsMapReads: Reads[Map[IdentityId, Boolean]] =
    Reads.mapReads[IdentityId, Boolean] { string =>
      PublicAssetPatchObject.validateIdentityId(string)
        .fold(e => JsError(e.getMessage), identityId => JsSuccess(identityId))
    }(mapMarkerReads)

  private implicit val identityIdSeqReads: Reads[Seq[IdentityId]] = jsValue =>
    identityIdsMapReads.reads(jsValue).flatMap(
      map => JsSuccess(map.keys.toSeq))

  private implicit val blobIdReads: Reads[JmapBlobId] = Json.valueReads[JmapBlobId]
  private implicit val unparsedIdentityIdReads: Reads[UnparsedIdentityId] = Json.valueReads[UnparsedIdentityId]
  private implicit val identityIdsReads: Reads[IdentityIds] = Json.valueReads[IdentityIds]
  private implicit val publicAssetCreationRequestReads: Reads[PublicAssetSetCreationRequest] = Json.reads[PublicAssetSetCreationRequest]
  private implicit val publicAssetCreationIdReads: Reads[PublicAssetCreationId] = Json.reads[PublicAssetCreationId]
  private implicit val unparsedPublicAssetIdReads: Reads[UnparsedPublicAssetId] = Json.valueReads[UnparsedPublicAssetId]

  private implicit val mapCreateRequestByPublicAssetCreationId: Reads[Map[PublicAssetCreationId, JsObject]] =
    Reads.mapReads[PublicAssetCreationId, JsObject] { string => Json.valueReads[PublicAssetCreationId].reads(JsString(string)) }

  private implicit val publicAssetPatchObjectReads: Reads[PublicAssetPatchObject] = Json.valueReads[PublicAssetPatchObject]
  private implicit val mapUnparsedPublicAssetIdPatchObject: Reads[Map[UnparsedPublicAssetId, PublicAssetPatchObject]] =
    Reads.mapReads[UnparsedPublicAssetId, PublicAssetPatchObject] { string => unparsedPublicAssetIdReads.reads(JsString(string)) }

  private implicit val publicAssetSetRequestReads: Reads[PublicAssetSetRequest] = Json.reads[PublicAssetSetRequest]

  private implicit val publicAssetGetReads: Reads[PublicAssetGetRequest] = Json.reads[PublicAssetGetRequest]

  private implicit val publicAssetIdWrites: Writes[PublicAssetId] = Json.valueWrites[PublicAssetId]
  private implicit val publicURIWrites: Writes[PublicURI] = Json.valueWrites[PublicURI]
  private implicit val imageContentTypeWrites: Writes[ImageContentType] = json => JsString(json.value)
  private implicit val identityIdWrites: Writes[IdentityId] = Json.valueWrites[IdentityId]
  private implicit val seqIdentityIdWrites: Writes[Seq[IdentityId]] = Writes.seq(identityIdWrites)

  private implicit val publicAssetCreationResponseWrites: Writes[PublicAssetCreationResponse] = Json.writes[PublicAssetCreationResponse]

  implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]
  implicit val publicAssetCreationIdWrites: Writes[PublicAssetCreationId] = Json.writes[PublicAssetCreationId]

  implicit val mapPublicAssetCreationIdSetErrorWrites: Writes[Map[PublicAssetCreationId, SetError]] =
    mapWrites[PublicAssetCreationId, SetError](_.id.value, setErrorWrites)

  implicit val mapPublicAssetCreationIdPublicAssetCreationResponseWrites: Writes[Map[PublicAssetCreationId, PublicAssetCreationResponse]] =
    mapWrites[PublicAssetCreationId, PublicAssetCreationResponse](_.id.value, publicAssetCreationResponseWrites)
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val updateResponseWrites: Writes[PublicAssetUpdateResponse] = _ => JsNull
  private implicit val mapPublicAssetUpdateResponse: Writes[Map[PublicAssetId, PublicAssetUpdateResponse]] =
    mapWrites[PublicAssetId, PublicAssetUpdateResponse](_.value.toString, updateResponseWrites)
  private implicit val mapNotUpdatedResponse: Writes[Map[UnparsedPublicAssetId, SetError]] =
    mapWrites[UnparsedPublicAssetId, SetError](_.id, setErrorWrites)

  private implicit val publicAssetSetResponseWrites: Writes[PublicAssetSetResponse] = Json.writes[PublicAssetSetResponse]
  private implicit val identityIdsMapWrites: Writes[Map[IdentityId, Boolean]] =
    mapWrites[IdentityId, Boolean](_.serialize, e => JsBoolean(e))

  private implicit val publicAssetWrites: Writes[PublicAssetDTO] = Json.writes[PublicAssetDTO]
  private implicit val publicAssetResponseWrites: Writes[PublicAssetGetResponse] = Json.writes[PublicAssetGetResponse]

  def deserializePublicAssetSetCreationRequest(input: JsValue): JsResult[PublicAssetSetCreationRequest] =
    Json.fromJson[PublicAssetSetCreationRequest](input)(publicAssetCreationRequestReads)

  def deserializePublicAssetSetRequest(input: JsValue): JsResult[PublicAssetSetRequest] =
    Json.fromJson[PublicAssetSetRequest](input)(publicAssetSetRequestReads)

  def deserializeGetRequest(input: JsValue): JsResult[PublicAssetGetRequest] = Json.fromJson[PublicAssetGetRequest](input)

  def serializePublicAssetCreationResponse(response: PublicAssetCreationResponse): JsValue =
    Json.toJson(response)(publicAssetCreationResponseWrites)

  def serializePublicAssetSetResponse(response: PublicAssetSetResponse): JsValue =
    Json.toJson(response)(publicAssetSetResponseWrites)

  def serializeGetResponse(response: PublicAssetGetResponse): JsValue = Json.toJson(response)
}
