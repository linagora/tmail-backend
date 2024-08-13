package com.linagora.tmail.james.jmap.publicAsset

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration.PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetServiceContract.{IDENTITY1, IDENTITY_ID1, PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED, identityRepository}
import org.apache.james.core.Username
import org.apache.james.jmap.api.identity.IdentityRepository
import org.apache.james.jmap.api.model.{HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, Size, TextSignature}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.Mockito.{mock, when}
import reactor.core.scala.publisher.{SFlux, SMono}

object PublicAssetServiceContract {

  val USERNAME: Username = Username.of("username@domain.tld")
  val IDENTITY_ID1: IdentityId = IdentityId.generate
  val IDENTITY1: Identity = Identity(id = IDENTITY_ID1,
    name = IdentityName(""),
    email = USERNAME.asMailAddress(),
    replyTo = None,
    bcc = None,
    textSignature = TextSignature("text signature"),
    htmlSignature = HtmlSignature("html signature"),
    mayDelete = MayDeleteIdentity(true))

  val identityRepository: IdentityRepository = mock(classOf[IdentityRepository])

  val PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED: Long = PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT.asLong()
}

trait PublicAssetServiceContract {

  import PublicAssetRepositoryContract._

  def testee: PublicAssetSetService

  def publicAssetRepository: PublicAssetRepository

  def blobIdFactory = BLOBID_FACTORY

  @BeforeEach
  def setUp(): Unit = {
    when(identityRepository.list(USERNAME)).thenReturn(SFlux.fromIterable(Seq(IDENTITY1)))
  }

  @Test
  def cleanUpShouldRemovePublicAssetHasEmptyIdentityIds(): Unit = {
    // Given publicAsset has empty IdentityIds
    val publicAsset: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME,
      CREATION_REQUEST.copy(identityIds = Seq.empty))).block()

    // When cleanUpPublicAsset is called
    testee.cleanUpPublicAsset(USERNAME).block()

    // Then publicAsset should be removed
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset.id)).block()).isNull()
  }

  @Test
  def cleanUpShouldRemovePublicAssetHasAllIdentityIdsNotExist(): Unit = {
    // Given publicAsset has all IdentityIds not exist
    val notExistIdentityId = IdentityId.generate
    val publicAsset: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, CREATION_REQUEST
      .copy(identityIds = Seq(notExistIdentityId)))).block()

    // When cleanUpPublicAsset is called
    testee.cleanUpPublicAsset(USERNAME).block()

    // Then publicAsset should be removed
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset.id)).block()).isNull()
  }

  @Test
  def cleanUpShouldNotRemovePublicAssetHasAnyIdentityIdsExist(): Unit = {
    // Given publicAsset has identityId exist and not exist
    val notExistIdentityId = IdentityId.generate
    val existIdentityId = IDENTITY_ID1
    val publicAsset: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME,
      CREATION_REQUEST.copy(identityIds = Seq(existIdentityId, notExistIdentityId)))).block()

    // When cleanUpPublicAsset is called
    val totalSize: Long = testee.cleanUpPublicAsset(USERNAME).block()

    // Then publicAsset should not be removed
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset.id)).block()).isNotNull()
  }

  @Test
  def cleanUpShouldNotRemovePublicAssetHasAllIdentityIdsExist(): Unit = {
    // Given publicAsset has all IdentityIds exist
    val identityId2 = IdentityId.generate
    when(identityRepository.list(USERNAME)).thenReturn(SFlux.fromIterable(Seq(IDENTITY1, IDENTITY1.copy(id = identityId2))))

    val publicAsset: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, CREATION_REQUEST
      .copy(identityIds = Seq(IDENTITY_ID1, identityId2)))).block()

    // When cleanUpPublicAsset is called
    val totalSize: Long = testee.cleanUpPublicAsset(USERNAME).block()

    assertThat(totalSize).isEqualTo(0)

    // Then publicAsset should not be removed
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset.id)).block()).isNotNull()
  }

  @Test
  def cleanUpShouldReturnTotalSizeOfAllRemovedPublicAsset(): Unit = {
    val creationRequest1 = CREATION_REQUEST.copy(size = Size.sanitizeSize(7),
      identityIds = Seq())
    val creationRequest2 = CREATION_REQUEST.copy(size = Size.sanitizeSize(8),
      identityIds = Seq(IdentityId.generate))
    SMono(publicAssetRepository.create(USERNAME, creationRequest1)).block()
    SMono(publicAssetRepository.create(USERNAME, creationRequest2)).block()

    assertThat(testee.cleanUpPublicAsset(USERNAME).block())
      .isEqualTo(15)
  }

  @Test
  def createShouldCleanUpPublicAssetWhenQuotaLimitExceeded(): Unit = {
    // Given publicAsset has size < PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED, and identityId does not exist
    val creationRequest = CREATION_REQUEST.copy(size = Size.sanitizeSize(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED - 100), identityIds = Seq(IdentityId.generate))
    val publicAsset: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequest)).block()

    // When create is called, the new publicAsset will exceed the limit
    val newPublicAsset: PublicAssetStorage = SMono(testee.create(USERNAME, creationRequest.copy(
      size = Size.sanitizeSize(200),
      identityIds = Seq(IDENTITY_ID1)
    ))).block()

    // Then publicAsset should be removed
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset.id)).block()).isNull()

    // And new publicAsset should be created
    assertThat(SMono(publicAssetRepository.get(USERNAME, newPublicAsset.id)).block()).isNotNull
  }

  @Test
  def createShouldFailWhenCanNotCleanUpAnyPublicAsset(): Unit = {
    // Given publicAssetA has size < PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED, and identityId exist
    val creationRequest = CREATION_REQUEST.copy(size = Size.sanitizeSize(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED - 100), identityIds = Seq(IDENTITY_ID1))
    val publicAssetA: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequest)).block()

    // When create is called, the new publicAsset will exceed the limit
    val exception = assertThrows(classOf[PublicAssetQuotaLimitExceededException],
      () => testee.create(USERNAME, creationRequest.copy(
        size = Size.sanitizeSize(200),
        identityIds = Seq(IDENTITY_ID1)
      )).block())

    // Then publicAssetA should not be removed
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAssetA.id)).block()).isNotNull

    // And exception should be thrown
    assertThat(exception).isNotNull
  }

  @Test
  def createShouldFailWhenAfterCleanUpButCanNotReleaseAsLeastSize(): Unit = {
    // Given publicAssetA has size < PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED, and identityId exist
    // and publicAssetB has size < 50, and identityId not exist
    // both publicAssetA and publicAssetB will be storage in the repository
    val creationRequestA: PublicAssetCreationRequest = CREATION_REQUEST.copy(size = Size.sanitizeSize(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED - 100), identityIds = Seq(IDENTITY_ID1))
    val publicAssetA: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequestA)).block()
    val creationRequestB: PublicAssetCreationRequest = CREATION_REQUEST.copy(size = Size.sanitizeSize(50), identityIds = Seq(IdentityId.generate))
    val publicAssetB: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequestB)).block()

    // When create is called, the new publicAsset will exceed the limit
    val exception = assertThrows(classOf[PublicAssetQuotaLimitExceededException],
      () => testee.create(USERNAME, creationRequestA.copy(
        size = Size.sanitizeSize(200),
        identityIds = Seq(IDENTITY_ID1)
      )).block())

    // Then publicAssetB should be removed
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAssetB.id)).block()).isNull()
    // And publicAssetA should not be removed
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAssetA.id)).block()).isNotNull

    // And exception should be thrown
    assertThat(exception).isNotNull
  }
}
