package com.linagora.tmail.james.jmap.publicAsset

import java.net.URI

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetRepositoryContract.{CREATION_REQUEST, USERNAME}
import org.apache.james.blob.api.{BucketName, PlainBlobId}
import org.apache.james.blob.memory.MemoryBlobStoreDAO
import org.apache.james.core.Username
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class PublicAssetDeletionTaskStepTest {
  val USERNAME_2: Username = Username.of("username2")
  val PUBLIC_ASSET_URI_PREFIX = new URI("http://localhost:8080")

  var publicAssetRepository: PublicAssetRepository = _
  var publicAssetDeletionTaskStep: PublicAssetDeletionTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    publicAssetRepository = new MemoryPublicAssetRepository(new DeDuplicationBlobStore(new MemoryBlobStoreDAO,
        BucketName.DEFAULT,
        new PlainBlobId.Factory()),
      JMAPExtensionConfiguration.PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT,
      PUBLIC_ASSET_URI_PREFIX)
    publicAssetDeletionTaskStep = new PublicAssetDeletionTaskStep(publicAssetRepository);
  }

  @Test
  def shouldRemovePublicAssets(): Unit = {
    SMono(publicAssetRepository.create(USERNAME, CREATION_REQUEST)).block()
    SMono(publicAssetDeletionTaskStep.deleteUserData(USERNAME)).block()

    assertThat(SFlux(publicAssetRepository.list(USERNAME)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def shouldNotRemoveOtherUsersAssets(): Unit = {
    SMono(publicAssetRepository.create(USERNAME, CREATION_REQUEST)).block()
    SMono(publicAssetRepository.create(USERNAME_2, CREATION_REQUEST)).block()

    SMono(publicAssetDeletionTaskStep.deleteUserData(USERNAME)).block()

    assertThat(SFlux(publicAssetRepository.list(USERNAME_2)).collectSeq().block().asJava)
      .hasSize(1)
  }

  @Test
  def shouldBeIdempotent(): Unit = {
    SMono(publicAssetDeletionTaskStep.deleteUserData(USERNAME)).block()

    assertThatCode(() => SMono(publicAssetDeletionTaskStep.deleteUserData(USERNAME)).block())
      .doesNotThrowAnyException()
  }
}
