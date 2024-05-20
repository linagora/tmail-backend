package com.linagora.tmail.james.jmap.publicAsset

import java.net.URI
import java.util.UUID

import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import eu.timepit.refined
import eu.timepit.refined.api.{Refined, Validate}
import org.apache.http.client.utils.URIBuilder
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.mail.BlobId
import org.apache.james.mailbox.model.ContentType

import scala.util.Try

object PublicAssetIdFactory {
  def generate(): PublicAssetId = PublicAssetId(UUID.randomUUID())
}

case class PublicAssetId(value: UUID) {
  def asString(): String = value.toString
}

object PublicURI {
  def fromString(value: String): Either[Throwable, PublicURI] = Try(new URI(value))
    .map(PublicURI.apply)
    .toEither

  def from(assetId: PublicAssetId, account: AccountId, publicUriPrefix: URI): PublicURI = {
    val baseUri = new URIBuilder(publicUriPrefix)
    val initPathSegments = baseUri.getPathSegments
    val finalPathSegments = ImmutableList.builder()
      .addAll(initPathSegments)
      .add("publicAsset")
      .add(account.id.value)
      .add(assetId.asString())
      .build()
    PublicURI(baseUri.setPathSegments(finalPathSegments)
      .build())
  }
}

case class PublicURI(value: URI) extends AnyVal

object ImageContentType {
  def from(contentType: ContentType): Either[PublicAssetInvalidContentTypeException, ImageContentType] =
    validate(contentType.asString())

  case class ImageContentTypeConstraint()

  type ImageContentType = String Refined ImageContentTypeConstraint

  implicit val imageContentType: Validate.Plain[String, ImageContentTypeConstraint] =
    Validate.fromPredicate(
      str => str.startsWith("image/"),
      str => s"$str starts with 'image/'",
      ImageContentTypeConstraint())

  def validate(string: String): Either[PublicAssetInvalidContentTypeException, ImageContentType] =
    refined.refineV[ImageContentTypeConstraint](string)
      .left
      .map(e => PublicAssetInvalidContentTypeException(e))
}

trait PublicAssetException extends RuntimeException {
  def message: String

  override def getMessage: String = message
}

case class PublicAssetInvalidContentTypeException(contentType: String) extends PublicAssetException {
  override val message: String = s"Invalid content type: $contentType"
}

case class PublicAssetNotFoundException(id: PublicAssetId) extends PublicAssetException {
  override val message: String = s"Public asset not found: ${id.asString()}"
}

case class PublicAsset(id: PublicAssetId,
                       publicURI: PublicURI,
                       size: Size,
                       contentType: ImageContentType,
                       blobId: BlobId,
                       identityIds: Seq[IdentityId] = Seq.empty) {
  def sizeAsLong(): java.lang.Long = size.value

  def contentTypeAsString(): String = contentType.value
}