package com.linagora.tmail.james.jmap.publicAsset

import java.util.UUID

import cats.implicits._
import com.linagora.tmail.james.jmap.json.PublicAssetSerializer.PublicAssetSetUpdateReads
import com.linagora.tmail.james.jmap.method.PublicAssetSetMethod.LOGGER
import com.linagora.tmail.james.jmap.method.standardErrorMessage
import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Properties, SetError, UuidState}
import org.apache.james.jmap.mail.{IdentityIds, BlobId => JmapBlobId}
import org.apache.james.jmap.method.{WithAccountId, standardError}
import org.apache.james.jmap.routes.BlobNotFoundException
import play.api.libs.json.{JsArray, JsError, JsObject, JsString, JsSuccess, JsValue}

import scala.util.Try

case class PublicAssetSetRequest(accountId: AccountId,
                                 create: Option[Map[PublicAssetCreationId, JsObject]],
                                 update: Option[Map[UnparsedPublicAssetId, PublicAssetPatchObject]],
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

case class UnparsedPublicAssetId(id: String) {

  def tryAsPublicAssetId: Either[IllegalArgumentException, PublicAssetId] =
    PublicAssetId.fromString(id).toEither
      .left.map {
        case e: IllegalArgumentException => e
        case e => new IllegalArgumentException(e)
      }
}

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
                                  notCreated: Option[Map[PublicAssetCreationId, SetError]],
                                  updated: Option[Map[PublicAssetId, PublicAssetUpdateResponse]],
                                  notUpdated: Option[Map[UnparsedPublicAssetId, SetError]])

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
      LOGGER.warn("Unexpected exception when create public asset", exception)
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

object PublicAssetPatchObject {
  val identityIdsProperty: String = "identityIds"

  def validateIdentityId(identityId: String): Either[IllegalArgumentException, IdentityId] =
    Try(UUID.fromString(identityId))
      .toEither
      .map(IdentityId(_))
      .left.map {
        case e: IllegalArgumentException => e
        case e => new IllegalArgumentException(e)
      }
}

case class PublicAssetPatchObject(value: JsObject) {

  def validate: Either[PublicAssetPatchUpdateValidationException, ValidatedPublicAssetPatchObject] =
    (PublicAssetSetUpdateReads.reads(value) match {
      case JsError(errors) => Left(PublicAssetPatchUpdateValidationException(standardErrorMessage(errors)))
      case JsSuccess(patchObject, _) => Right(patchObject)
    }).flatMap(e => e.validate())
}

case class PublicAssetPatchUpdateValidationException(error: String) extends PublicAssetException {
  override val message: String = error
}

case class ValidatedPublicAssetPatchObject(resetIdentityIds: Option[Seq[IdentityId]],
                                           identityIdsToAdd: Seq[IdentityId] = Seq.empty,
                                           identityIdsToRemove: Seq[IdentityId] = Seq.empty) {

  // if resetIdentityIds is defined, then identityIdsToAdd and identityIdsToRemove must be empty
  // if resetIdentityIds is not defined, then identityIdsToAdd and identityIdsToRemove must not be empty
  // the value of identityIdsToAdd and identityIdsToRemove must be not conflict with each other
  def validate(): Either[PublicAssetPatchUpdateValidationException, ValidatedPublicAssetPatchObject] =
    resetIdentityIds match {
      case Some(_) => (identityIdsToAdd, identityIdsToRemove) match {
        case (Seq(), Seq()) => Right(this)
        case _ => Left(PublicAssetPatchUpdateValidationException("Cannot reset identityIds and add/remove identityIds at the same time"))
      }
      case None => (identityIdsToAdd, identityIdsToRemove) match {
        case (Seq(), Seq()) => Left(PublicAssetPatchUpdateValidationException("Cannot update identityIds with empty request"))
        case _ => if (identityIdsToAdd.intersect(identityIdsToRemove).nonEmpty)
          Left(PublicAssetPatchUpdateValidationException("Cannot add and remove the same identityId at the same time"))
        else
          Right(this)
      }
    }

  def isReset: Boolean = resetIdentityIds.isDefined
}

case class PublicAssetUpdateResponse()

sealed trait PublicAssetUpdateResult

case class PublicAssetUpdateFailure(id: UnparsedPublicAssetId, exception: Throwable) extends PublicAssetUpdateResult {
  def asSetError: SetError = exception match {
    case e: PublicAssetException =>
      LOGGER.info("Has an error when update public asset ", e)
      SetError.invalidArguments(SetErrorDescription(e.getMessage))
    case e: IllegalArgumentException =>
      LOGGER.info("Has an error when update public asset ", e)
      SetError.invalidArguments(SetErrorDescription(e.getMessage))
    case _ =>
      LOGGER.error("Unexpected exception when update public asset ", exception)
      SetError.serverFail(SetErrorDescription(exception.getMessage))
  }
}

case class PublicAssetUpdateSuccess(id: PublicAssetId) extends PublicAssetUpdateResult

case class PublicAssetUpdateResults(results: Seq[PublicAssetUpdateResult]) {
  def updated: Map[PublicAssetId, PublicAssetUpdateResponse] =
    results.flatMap(result => result match {
      case success: PublicAssetUpdateSuccess => Some((success.id, PublicAssetUpdateResponse()))
      case _ => None
    }).toMap

  def notUpdated: Map[UnparsedPublicAssetId, SetError] =
    results.flatMap(result => result match {
      case failure: PublicAssetUpdateFailure => Some(failure.id, failure.asSetError)
      case _ => None
    }).toMap
}