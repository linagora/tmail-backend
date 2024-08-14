package com.linagora.tmail.james.jmap.publicAsset

import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.util.UUID

import com.github.f4b6a3.uuid.UuidCreator
import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import eu.timepit.refined
import eu.timepit.refined.api.{Refined, Validate}
import org.apache.http.client.utils.URIBuilder
import org.apache.james.blob.api.BlobId
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.core.JmapRfc8621Configuration
import org.apache.james.mailbox.model.ContentType

import scala.util.Try

object PublicAssetIdFactory {
  def generate(): PublicAssetId = PublicAssetId(UuidCreator.getTimeBased)

  def from(value: String): Either[(String, IllegalArgumentException), PublicAssetId] =
    Try(PublicAssetId(UUID.fromString(value)))
      .toEither
      .left.map(e => value -> new IllegalArgumentException(e))
}

object PublicAssetId {
  def fromString(value: String): Try[PublicAssetId] =
    Try(PublicAssetId(UUID.fromString(value)))
}

case class PublicAssetId(value: UUID) {
  def asString(): String = value.toString
}

object PublicAssetURIPrefix {
  def fromConfiguration(configuration: JmapRfc8621Configuration): Either[Throwable, URI] =
    Try(new URI(configuration.urlPrefixString)).toEither
}

object PublicURI {
  def fromString(value: String): Either[Throwable, PublicURI] = Try(new URI(value))
    .map(PublicURI.apply)
    .toEither

  def from(assetId: PublicAssetId, username: Username, publicUriPrefix: URI): PublicURI = {
    val baseUri = new URIBuilder(publicUriPrefix)
    val initPathSegments = baseUri.getPathSegments
    val finalPathSegments = ImmutableList.builder()
      .addAll(initPathSegments)
      .add("publicAsset")
      .add(username.asString())
      .add(assetId.asString())
      .build()
    PublicURI(baseUri.setPathSegments(finalPathSegments)
      .build())
  }
}

case class PublicURI(value: URI) extends AnyVal

object ImageContentType {
  def from(contentType: ContentType): Either[PublicAssetInvalidContentTypeException, ImageContentType] =
    validate(contentType.mimeType().asString())

  def from(contentType: String): Either[PublicAssetInvalidContentTypeException, ImageContentType] =
    validate(contentType)

  case class ImageContentTypeConstraint()

  type ImageContentType = String Refined ImageContentTypeConstraint

  implicit val imageContentType: Validate.Plain[String, ImageContentTypeConstraint] =
    Validate.fromPredicate(
      str => str.startsWith("image/"),
      str => s"'$str' is not a valid image content type. A valid image content type should start with 'image/'",
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

case class PublicAssetQuotaLimitExceededException() extends PublicAssetException {
  override val message: String = "Exceeding public asset quota limit"
}

case class PublicAssetStorage(id: PublicAssetId,
                              publicURI: PublicURI,
                              size: Size,
                              contentType: ImageContentType,
                              blobId: BlobId,
                              identityIds: Seq[IdentityId] = Seq.empty,
                              content: () => InputStream) {
  def sizeAsLong(): java.lang.Long = size.value

  def contentTypeAsString(): String = contentType.value
}

case class PublicAssetCreationRequest(size: Size,
                                      contentType: ImageContentType,
                                      identityIds: Seq[IdentityId] = Seq.empty,
                                      content: () => InputStream)

object PublicAssetMetadata {
  def from(publicAsset: PublicAssetStorage): PublicAssetMetadata =
    PublicAssetMetadata(
      publicAsset.id,
      publicAsset.publicURI,
      publicAsset.size,
      publicAsset.contentType,
      publicAsset.blobId,
      publicAsset.identityIds)
}

case class PublicAssetMetadata(id: PublicAssetId,
                               publicURI: PublicURI,
                               size: Size,
                               contentType: ImageContentType,
                               blobId: BlobId,
                               identityIds: Seq[IdentityId],
                               createdDate: Instant = Instant.now()) {

  def asPublicAssetStorage(content: InputStream): PublicAssetStorage =
    PublicAssetStorage(id = id,
      publicURI = publicURI,
      size = size,
      contentType = contentType,
      blobId = blobId,
      identityIds = identityIds,
      content = () => content)

  def sizeAsLong(): java.lang.Long = size.value
}