package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import com.linagora.tmail.james.jmap.publicAsset.{PublicAssetCreationId, PublicAssetCreationResponse, PublicAssetId, PublicAssetSetCreationRequest, PublicAssetSetRequest, PublicAssetSetResponse, PublicURI, UnparsedPublicAssetId}
import org.apache.james.blob.api.BlobId
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.core.{SetError, UuidState}
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.{IdentityIds, UnparsedIdentityId, BlobId => JmapBlobId}
import play.api.libs.json.{JsObject, JsResult, JsString, JsValue, Json, Reads, Writes}

object PublicAssetSerializer {
  private implicit val blobIdReads: Reads[JmapBlobId] = Json.valueReads[JmapBlobId]
  private implicit val unparsedIdentityIdReads: Reads[UnparsedIdentityId] = Json.valueReads[UnparsedIdentityId]
  private implicit val identityIdsReads: Reads[IdentityIds] = Json.valueReads[IdentityIds]
  private implicit val publicAssetCreationRequestReads: Reads[PublicAssetSetCreationRequest] = Json.reads[PublicAssetSetCreationRequest]
  private implicit val publicAssetCreationIdReads: Reads[PublicAssetCreationId] = Json.reads[PublicAssetCreationId]
  private implicit val unparsedPublicAssetIdReads: Reads[UnparsedPublicAssetId] = Json.reads[UnparsedPublicAssetId]

  private implicit val mapCreateRequestByPublicAssetCreationId: Reads[Map[PublicAssetCreationId, JsObject]] =
    Reads.mapReads[PublicAssetCreationId, JsObject] { string => Json.valueReads[PublicAssetCreationId].reads(JsString(string)) }

  private implicit val mapUnparsedLabelIdJsObject: Reads[Map[UnparsedPublicAssetId, JsObject]] =
    Reads.mapReads[UnparsedPublicAssetId, JsObject] { string => unparsedPublicAssetIdReads.reads(JsString(string)) }

  private implicit val publicAssetSetRequestReads: Reads[PublicAssetSetRequest] = Json.reads[PublicAssetSetRequest]

  private implicit val publicAssetIdWrites: Writes[PublicAssetId] = Json.valueWrites[PublicAssetId]
  private implicit val blobIdWrites: Writes[BlobId] = value => JsString(value.asString())
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
  private implicit val publicAssetSetResponseWrites: Writes[PublicAssetSetResponse] = Json.writes[PublicAssetSetResponse]

  def deserializePublicAssetSetCreationRequest(input: JsValue): JsResult[PublicAssetSetCreationRequest] =
    Json.fromJson[PublicAssetSetCreationRequest](input)(publicAssetCreationRequestReads)

  def deserializePublicAssetSetRequest(input: JsValue): JsResult[PublicAssetSetRequest] =
    Json.fromJson[PublicAssetSetRequest](input)(publicAssetSetRequestReads)

  def serializePublicAssetCreationResponse(response: PublicAssetCreationResponse): JsValue =
    Json.toJson(response)(publicAssetCreationResponseWrites)

  def serializePublicAssetSetResponse(response: PublicAssetSetResponse): JsValue =
    Json.toJson(response)(publicAssetSetResponseWrites)
}