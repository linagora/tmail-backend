package com.linagora.tmail.james.jmap.publicAsset

import cats.implicits._
import com.linagora.tmail.james.jmap.method.PublicAssetSetCreatePerformer.LOGGER
import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import org.apache.james.blob.api.BlobId
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Properties, SetError, UuidState}
import org.apache.james.jmap.mail.{IdentityIds, BlobId => JmapBlobId}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.jmap.routes.BlobNotFoundException
import play.api.libs.json.JsObject

case class PublicAssetSetRequest(accountId: AccountId,
                                 create: Option[Map[PublicAssetCreationId, JsObject]],
                                 update: Option[Map[UnparsedPublicAssetId, JsObject]],
                                 destroy: Option[Seq[UnparsedPublicAssetId]]) extends WithAccountId

case class PublicAssetCreationId(id: Id)

object PublicAssetSetCreationRequest {
  val knownProperties: Set[String] = Set("blobId", "identityIds")

  def validateProperties(jsObject: JsObject): Either[PublicAssetCreationParseException, JsObject] = {
    jsObject.fields.find(mapEntry => !knownProperties.contains(mapEntry._1))
      .map(e => Left(PublicAssetCreationParseException(SetError.invalidArguments(
        SetErrorDescription("Some unknown properties were specified"),
        Some(Properties.toProperties(Set(e._1)))))))
      .getOrElse(Right(jsObject))
  }
}

case class PublicAssetSetCreationRequest(blobId: JmapBlobId, identityIds: Option[IdentityIds] = None) {

  def parseIdentityIds: Either[PublicAssetInvalidIdentityIdException, List[IdentityId]] =
    identityIds match {
      case None => Right(List.empty)
      case Some(value) if value.ids.isEmpty => Right(List.empty)
      case Some(identityIds) => identityIds.ids
        .map(unparsedIdentityId => unparsedIdentityId.validate
          .fold(_ => Left(PublicAssetInvalidIdentityIdException(unparsedIdentityId.id.value)), e1 => Right(e1)))
        .sequence
    }
}

case class UnparsedPublicAssetId(id: String)

object PublicAssetCreationResponse {
  def from(publicAsset: PublicAssetStorage): PublicAssetCreationResponse = {
    PublicAssetCreationResponse(publicAsset.id,
      publicAsset.publicURI,
      publicAsset.size.value,
      publicAsset.contentType)
  }
}

case class PublicAssetCreationResponse(id: PublicAssetId,
                                       publicURI: PublicURI,
                                       size: Long,
                                       contentType: ImageContentType)

case class PublicAssetSetResponse(accountId: AccountId,
                                  oldState: Option[UuidState],
                                  newState: UuidState,
                                  created: Option[Map[PublicAssetCreationId, PublicAssetCreationResponse]],
                                  notCreated: Option[Map[PublicAssetCreationId, SetError]])

case class PublicAssetCreationParseException(setError: SetError) extends PublicAssetException {
  override val message: String = s"Invalid public asset creation request: ${setError.description}"
}

case class PublicAssetInvalidBlobIdException(blobId: String) extends PublicAssetException {
  override val message: String = s"Invalid blobId: $blobId"
}

case class PublicAssetBlobIdNotFoundException(blobId: String) extends PublicAssetException {
  override val message: String = s"BlobId not found: $blobId"
}

case class PublicAssetInvalidIdentityIdException(identityId: String) extends PublicAssetException {
  override val message: String = s"Invalid identityId: $identityId"
}

case class PublicAssetIdentityIdNotFoundException(identityIds: Seq[IdentityId]) extends PublicAssetException {
  override val message: String = s"IdentityId not found: ${identityIds.map(_.id.toString).mkString(", ")}"
}

sealed trait PublicAssetCreationResult {
  def publicAssetCreationId: PublicAssetCreationId
}

case class PublicAssetCreationSuccess(publicAssetCreationId: PublicAssetCreationId, publicAssetCreationResponse: PublicAssetCreationResponse) extends PublicAssetCreationResult

case class PublicAssetCreationFailure(publicAssetCreationId: PublicAssetCreationId, exception: Throwable) extends PublicAssetCreationResult {
  def asPublicAssetSetError: SetError = exception match {
    case e: PublicAssetCreationParseException => e.setError
    case e: PublicAssetException => SetError.invalidArguments(SetError.SetErrorDescription(e.getMessage))
    case e: BlobNotFoundException => SetError.invalidArguments(SetError.SetErrorDescription(e.getMessage))
    case e: IllegalArgumentException => SetError.invalidArguments(SetError.SetErrorDescription(e.getMessage))
    case _ =>
      LOGGER.warn("Unexpected exception", exception)
      SetError.serverFail(SetError.SetErrorDescription(exception.getMessage))
  }
}

case class PublicAssetCreationResults(created: Seq[PublicAssetCreationResult]) {
  def retrieveCreated: Map[PublicAssetCreationId, PublicAssetCreationResponse] = created
    .flatMap(result => result match {
      case success: PublicAssetCreationSuccess => Some(success.publicAssetCreationId, success.publicAssetCreationResponse)
      case _ => None
    })
    .toMap
    .map(creation => (creation._1, creation._2))

  def retrieveErrors: Map[PublicAssetCreationId, SetError] = created
    .flatMap(result => result match {
      case failure: PublicAssetCreationFailure => Some(failure.publicAssetCreationId, failure.asPublicAssetSetError)
      case _ => None
    }).toMap
}