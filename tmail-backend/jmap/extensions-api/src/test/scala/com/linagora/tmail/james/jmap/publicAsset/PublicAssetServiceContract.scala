package com.linagora.tmail.james.jmap.publicAsset

import java.time.Instant
import java.time.temporal.ChronoUnit

import com.linagora.tmail.james.jmap.PublicAssetTotalSizeLimit
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetServiceContract.{CLOCK, IDENTITY1, IDENTITY_ID1, PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED, identityRepository}
import org.apache.james.core.Username
import org.apache.james.jmap.api.identity.IdentityRepository
import org.apache.james.jmap.api.model.{HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, Size, TextSignature}
import org.apache.james.utils.UpdatableTickingClock
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

  val PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED: Long = PublicAssetTotalSizeLimit.DEFAULT.asLong()

  val CLOCK = new UpdatableTickingClock(Instant.now())
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
    testee.cleanUpPublicAsset(USERNAME, PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED).block()

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
    testee.cleanUpPublicAsset(USERNAME, PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED).block()

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
    val totalSize: Long = testee.cleanUpPublicAsset(USERNAME, PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED).block()

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
    val totalSize: Long = testee.cleanUpPublicAsset(USERNAME, PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED).block()

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

    assertThat(testee.cleanUpPublicAsset(USERNAME, PUBLIC_ASSET_TOTAL_SIZE_LIMIT_IN_CONFIGURED).block())
      .isEqualTo(15)
  }

  @Test
  def cleanUpShouldRemovePublicAssetUntilMinimumThresholdIsSatisfied(): Unit = {
    val creationRequest1 = CREATION_REQUEST.copy(size = Size.sanitizeSize(7),
      identityIds = Seq())
    val creationRequest2 = CREATION_REQUEST.copy(size = Size.sanitizeSize(7),
      identityIds = Seq(IdentityId.generate))

    SMono(publicAssetRepository.create(USERNAME, creationRequest1)).block()
    SMono(publicAssetRepository.create(USERNAME, creationRequest2)).block()

    assertThat(testee.cleanUpPublicAsset(USERNAME, 7).block())
      .isEqualTo(7)
  }

  @Test
  def cleanUpShouldRemoveOldestPublicAssetFirst(): Unit = {
    // Given publicAsset1, publicAsset2, publicAsset3, with publicAsset3 is the oldest create date
    val creationRequest1 = CREATION_REQUEST.copy(size = Size.sanitizeSize(7), identityIds = Seq())
    val creationRequest2 = CREATION_REQUEST.copy(size = Size.sanitizeSize(7), identityIds = Seq(IdentityId.generate))
    val creationRequest3 = CREATION_REQUEST.copy(size = Size.sanitizeSize(7), identityIds = Seq(IdentityId.generate))

    val publicAsset3: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequest3)).block()
    Thread.sleep(100)
    val publicAsset2: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequest2)).block()
    Thread.sleep(100)
    val publicAsset1: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequest1)).block()

    // when cleanUpPublicAsset is called
    assertThat(testee.cleanUpPublicAsset(USERNAME, 7).block())
      .isEqualTo(7)

    // then publicAsset3 should be removed, because it is the oldest
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset3.id)).block()).isNull()

    // and publicAsset1 and publicAsset2 should not be removed, because they are newer and cleanup size is satisfied
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset1.id)).block()).isNotNull
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset2.id)).block()).isNotNull
  }

  @Test
  def cleanUpShouldWorkCorrectWhenMixCase(): Unit = {
    // Given publicAsset1,2,3,4
    // with publicAsset4 is the oldest create date, but it has exists identityIds
    val creationRequest1 = CREATION_REQUEST.copy(size = Size.sanitizeSize(7), identityIds = Seq())
    val creationRequest2 = CREATION_REQUEST.copy(size = Size.sanitizeSize(8), identityIds = Seq(IdentityId.generate))
    val creationRequest3 = CREATION_REQUEST.copy(size = Size.sanitizeSize(9), identityIds = Seq(IdentityId.generate))
    val creationRequest4 = CREATION_REQUEST.copy(size = Size.sanitizeSize(10), identityIds = Seq(IDENTITY_ID1))

    val publicAsset4: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequest4)).block()
    CLOCK.setInstant(Instant.now().minus(10, ChronoUnit.MINUTES))
    val publicAsset3: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequest3)).block()
    CLOCK.setInstant(Instant.now().minus(40, ChronoUnit.MINUTES))
    val publicAsset2: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequest2)).block()
    CLOCK.setInstant(Instant.now().minus(30, ChronoUnit.MINUTES))
    val publicAsset1: PublicAssetStorage = SMono(publicAssetRepository.create(USERNAME, creationRequest1)).block()

    // when cleanUpPublicAsset is called
    assertThat(testee.cleanUpPublicAsset(USERNAME, 10).block())
      .isEqualTo(8 + 9)

    // then publicAsset4 should not be removed, because it has exists identityIds
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset4.id)).block()).isNotNull

    // and publicAsset2, 3 should be removed, because they are the older than publicAsset1, and cleanup size is satisfied
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset2.id)).block()).isNull()
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset3.id)).block()).isNull()
    // and publicAsset1 should not be removed, because it is the newest and cleanup size is satisfied
    assertThat(SMono(publicAssetRepository.get(USERNAME, publicAsset1.id)).block()).isNotNull
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
