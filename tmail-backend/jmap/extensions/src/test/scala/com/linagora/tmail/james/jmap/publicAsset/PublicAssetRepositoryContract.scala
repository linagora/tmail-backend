package com.linagora.tmail.james.jmap.publicAsset

import java.net.URI

import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.api.model.{IdentityId, Size}
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.mail.BlobId
import org.apache.james.mailbox.model.ContentType
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object PublicAssetRepositoryContract {
  val USERNAME: Username = Username.of("username1")
  val PUBLIC_ASSET_ID: PublicAssetId = PublicAssetIdFactory.generate()
  val CONTENT_TYPE: ContentType = ContentType.of("image/png")
  val IMAGE_CONTENT_TYPE: ImageContentType = ImageContentType.from(CONTENT_TYPE).toOption.get
  val IDENTITY_IDS: Seq[IdentityId] = Seq(IdentityId.generate, IdentityId.generate)
  val ASSET_CONTENT: Array[Byte] = Array[Byte](1, 2, 3)
  val SIZE: Size = Size.sanitizeSize(ASSET_CONTENT.length)

  val CREATION_REQUEST: PublicAsset = PublicAsset(id = PUBLIC_ASSET_ID,
    publicURI = PublicURI.from(PUBLIC_ASSET_ID, AccountId.from(USERNAME).toOption.get, new URI("http://localhost:8080/")),
    size = SIZE,
    contentType = IMAGE_CONTENT_TYPE,
    blobId = BlobId("blob1"),
    identityIds = Seq.empty)
}

trait PublicAssetRepositoryContract {

  import PublicAssetRepositoryContract._

  def teste: PublicAssetRepository

  @Test
  def createShouldReturnPublicAssetWhenSuccess(): Unit = {
    // When creating a public asset
    val publicAsset = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()

    // Then the public asset should has the expected values
    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(publicAsset.sizeAsLong()).isEqualTo(ASSET_CONTENT.length)
      softly.assertThat(publicAsset.contentType.value).isEqualTo("image/png")
      softly.assertThat(publicAsset.blobId.value.value).isEqualTo("blob1")
    })
  }

  @Test
  def createShouldStorePublicAssetWhenSuccess(): Unit = {
    // When creating a public asset
    val publicAsset = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()

    // Then the public asset should be stored
    val storedPublicAsset = SMono(teste.get(USERNAME, Set(publicAsset.id))).block()
    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(storedPublicAsset.sizeAsLong()).isEqualTo(ASSET_CONTENT.length)
      softly.assertThat(storedPublicAsset.contentType.value).isEqualTo("image/png")
      softly.assertThat(storedPublicAsset.blobId.value.value).isEqualTo(publicAsset.blobId.value.value)
    })
  }

  @Test
  def updateShouldSetIdentityIdsWhenSuccess(): Unit = {
    // Given a public asset
    val publicAsset = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()

    // When updating the public asset
    SMono(teste.update(USERNAME, publicAsset.id, IDENTITY_IDS.toSet)).block()

    // Then the public asset should has the expected identity ids
    val updatedPublicAsset = SMono(teste.get(USERNAME, Set(publicAsset.id))).block()
    assertThat(updatedPublicAsset.identityIds.asJava)
      .containsExactlyElementsOf(IDENTITY_IDS.asJava)
  }

  @Test
  def updateShouldFailWhenPublicAssetNotFound(): Unit = {
    assertThatThrownBy(() => SMono(teste.update(USERNAME, PUBLIC_ASSET_ID, IDENTITY_IDS.toSet)).block())
      .isInstanceOf(classOf[PublicAssetNotFoundException])
  }

  @Test
  def updateShouldSuccessWhenIdempotent(): Unit = {
    // Given a public asset
    val publicAsset = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()

    // When updating the public asset
    SMono(teste.update(USERNAME, publicAsset.id, IDENTITY_IDS.toSet)).block()

    // then updating the public asset again should success
    assertThatCode(() => SMono(teste.update(USERNAME, publicAsset.id, IDENTITY_IDS.toSet)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def removeShouldRemovePublicAssetWhenSuccess(): Unit = {
    // Given a public asset
    val publicAsset = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()

    // When revoking the public asset
    SMono(teste.remove(USERNAME, publicAsset.id)).block()

    // Then the public asset should be removed
    assertThat(SMono(teste.get(USERNAME, Set(publicAsset.id))).block()).isNull()
  }

  @Test
  def removeShouldNotFailWhenPublicAssetNotFound(): Unit = {
    val publicAssetIdNotFound = PublicAssetIdFactory.generate()
    assertThatCode(() => SMono(teste.remove(USERNAME, publicAssetIdNotFound)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def removeShouldRemoveAllPublicAssetsRelatedWhenSuccess(): Unit = {
    // Given a public asset
    val publicAsset1 = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()
    val publicAsset2 = SMono(teste.create(USERNAME, CREATION_REQUEST.copy(id = PublicAssetIdFactory.generate()))).block()

    assertThat(SFlux(teste.list(USERNAME)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(publicAsset1, publicAsset2)

    // When revoking all public assets
    SMono(teste.revoke(USERNAME)).block()

    // Then the public asset should be removed
    assertThat(SFlux(teste.list(USERNAME)).collectSeq().block().asJava).hasSize(0)
  }

  @Test
  def removeShouldNotRemovePublicAssetsFromOtherUsers(): Unit = {
    // Given a public asset
    val publicAsset1 = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()
    val publicAsset2 = SMono(teste.create(Username.of("username2"), CREATION_REQUEST.copy(id = PublicAssetIdFactory.generate()))).block()

    assertThat(SFlux(teste.list(USERNAME)).collectSeq().block().asJava)
      .containsExactly(publicAsset1)

    // When revoking all public assets
    SMono(teste.revoke(USERNAME)).block()

    // Then the public asset should be removed
    assertThat(SFlux(teste.list(Username.of("username2"))).collectSeq().block().asJava)
      .containsExactly(publicAsset2)
  }

  @Test
  def getShouldReturnEmptyWhenNoPublicAsset(): Unit = {
    val notFoundPublicAssetId = PublicAssetIdFactory.generate()
    assertThat(SFlux(teste.get(USERNAME, notFoundPublicAssetId)).collectSeq().block().asJava).isEmpty()
  }

  @Test
  def getShouldReturnPublicAssetWhenExists(): Unit = {
    // Given a public asset
    val publicAsset = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()

    // When getting the public asset
    val retrievedPublicAsset = SMono(teste.get(USERNAME, publicAsset.id)).block()

    // Then the public asset should be retrieved
    assertThat(retrievedPublicAsset).isEqualTo(publicAsset)
  }

  @Test
  def getShouldNotReturnPublicAssetFromOtherUsers(): Unit = {
    // Given a public asset
    val publicAsset = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()

    // When getting the public asset from another user
    assertThat(SMono(teste.get(Username.of("username2"), publicAsset.id)).block()).isNull()
  }

  @Test
  def listShouldReturnEmptyWhenNoPublicAsset(): Unit = {
    assertThat(SFlux(teste.list(USERNAME)).collectSeq().block().asJava).isEmpty()
  }

  @Test
  def listShouldReturnAllPublicAssets(): Unit = {
    // Given a public asset
    val publicAsset1 = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()
    val publicAsset2 = SMono(teste.create(USERNAME, CREATION_REQUEST.copy(id = PublicAssetIdFactory.generate()))).block()

    // When listing public assets
    val publicAssets = SFlux(teste.list(USERNAME)).collectSeq().block()

    // Then all public assets should be retrieved
    assertThat(publicAssets.asJava).containsExactlyInAnyOrder(publicAsset1, publicAsset2)
  }

  @Test
  def listShouldNotReturnPublicAssetsFromOtherUsers(): Unit = {
    // Given a public asset
    val publicAsset = SMono(teste.create(USERNAME, CREATION_REQUEST)).block()

    // When listing public assets from another user
    assertThat(SFlux(teste.list(Username.of("username2"))).collectSeq().block().asJava).isEmpty()
  }
}
