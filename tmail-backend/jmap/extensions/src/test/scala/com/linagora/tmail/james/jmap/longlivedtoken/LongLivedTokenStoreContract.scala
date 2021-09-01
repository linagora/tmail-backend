package com.linagora.tmail.james.jmap.longlivedtoken

import com.linagora.tmail.james.jmap.longlivedtoken.LongLivedTokenStoreContract.{DEVICE_ID, LONG_LIVED_TOKEN, LONG_LIVED_TOKEN_SECRET, USERNAME, USERNAME_2}
import org.apache.james.core.Username
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object LongLivedTokenStoreContract {
  private val USERNAME: Username = Username.of("bob")
  private val USERNAME_2: Username = Username.of("alice")
  private val DEVICE_ID: DeviceId = DeviceId("deviceId1")
  private val LONG_LIVED_TOKEN_SECRET: LongLivedTokenSecret = LongLivedTokenSecret.parse("89b8a916-463d-49b8-956e-80e2a64398d8").toOption.get
  private val LONG_LIVED_TOKEN: LongLivedToken = LongLivedToken(DEVICE_ID, LONG_LIVED_TOKEN_SECRET)
}

trait LongLivedTokenStoreContract {

  def getLongLivedTokenStore: LongLivedTokenStore

  var testee: LongLivedTokenStore = _

  @BeforeEach
  def setUp(): Unit = {
    testee = getLongLivedTokenStore
  }

  @Test
  def storeShouldThrowWhenUsernameIsNull(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.store(null, LONG_LIVED_TOKEN)).block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def storeShouldThrowWhenLongLivedTokenIsNull(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.store(USERNAME, null)).block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def storeShouldReturnLongLivedTokenId(): Unit = {
    assertThat(SMono.fromPublisher(testee.store(USERNAME, LONG_LIVED_TOKEN)).block())
      .isInstanceOf(classOf[LongLivedTokenId])
  }

  @Test
  def storeShouldReturnSameLongLivedTokenIdWhenSameDeviceIdAndSecret(): Unit = {
    val longLivedTokenId: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LONG_LIVED_TOKEN)).block()

    assertThat(SMono.fromPublisher(testee.store(USERNAME, LONG_LIVED_TOKEN)).block())
      .isEqualTo(longLivedTokenId)
  }

  @Test
  def storeShouldSaveLongLivedToken(): Unit = {
    val longLivedTokenId: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LONG_LIVED_TOKEN)).block()

    assertThat(SFlux.fromPublisher(testee.listTokens(USERNAME)).collectSeq().block().asJava)
      .contains(LongLivedTokenFootPrint(longLivedTokenId, DEVICE_ID))
  }

  @Test
  def validateShouldThrowWhenUsernameIsNull(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.validate(null, LONG_LIVED_TOKEN_SECRET)).block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def validateShouldThrowWhenLongLivedTokenSecretIsNull(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.validate(USERNAME, null)).block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def validateShouldThrowWhenLongLivedTokenSecretDoesNotExist(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.validate(USERNAME, LONG_LIVED_TOKEN_SECRET)).block())
      .isInstanceOf(classOf[LongLivedTokenNotFoundException])
  }

  @Test
  def validateShouldReturnLongLivedTokenFootPrintWhenSecretValid(): Unit = {
    val longLivedTokenId: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LONG_LIVED_TOKEN)).block()
    assertThat(SMono.fromPublisher(testee.validate(USERNAME, LONG_LIVED_TOKEN_SECRET)).block())
      .isEqualTo(LongLivedTokenFootPrint(longLivedTokenId, DEVICE_ID))
  }

  @Test
  def validateShouldThrowWhenLongLivedTokenSecretInvalid(): Unit = {
    SMono.fromPublisher(testee.store(USERNAME_2, LONG_LIVED_TOKEN)).block()

    assertThatThrownBy(() => SMono.fromPublisher(testee.validate(USERNAME, LONG_LIVED_TOKEN_SECRET)).block())
      .isInstanceOf(classOf[LongLivedTokenNotFoundException])
  }

  @Test
  def listTokensShouldThrowWhenUsernameIsNull(): Unit = {
    assertThatThrownBy(() => SFlux.fromPublisher(testee.listTokens(null)).collectSeq().block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def listTokensShouldReturnEmptyByDefault(): Unit = {
    assertThat(SFlux.fromPublisher(testee.listTokens(USERNAME)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listTokensShouldReturnStoredEntry(): Unit = {
    val longLivedTokenId: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LONG_LIVED_TOKEN)).block()

    assertThat(SFlux.fromPublisher(testee.listTokens(USERNAME)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(LongLivedTokenFootPrint(longLivedTokenId, DEVICE_ID))
  }

  @Test
  def listTokensShouldReturnStoredEntries(): Unit = {
    val longLivedTokenId1: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LongLivedToken(DEVICE_ID, LongLivedTokenSecret.generate))).block()
    val longLivedTokenId2: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LongLivedToken(DEVICE_ID, LongLivedTokenSecret.generate))).block()
    val deviceId2: DeviceId = DeviceId("deviceId2")
    val longLivedTokenId3: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LongLivedToken(deviceId2, LongLivedTokenSecret.generate))).block()

    assertThat(SFlux.fromPublisher(testee.listTokens(USERNAME)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(
        LongLivedTokenFootPrint(longLivedTokenId1, DEVICE_ID),
        LongLivedTokenFootPrint(longLivedTokenId2, DEVICE_ID),
        LongLivedTokenFootPrint(longLivedTokenId3, deviceId2),
      )
  }

  @Test
  def listTokensShouldNotReturnStoredEntriesOfOtherUser(): Unit = {
    val longLivedTokenId: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LongLivedToken(DEVICE_ID, LongLivedTokenSecret.generate))).block()
    val longLivedTokenIdOfOtherUser: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME_2, LongLivedToken(DEVICE_ID, LongLivedTokenSecret.generate))).block()

    assertThat(SFlux.fromPublisher(testee.listTokens(USERNAME_2)).collectSeq().block().asJava)
      .doesNotContain(LongLivedTokenFootPrint(longLivedTokenId, DEVICE_ID))
      .containsExactlyInAnyOrder(LongLivedTokenFootPrint(longLivedTokenIdOfOtherUser, DEVICE_ID))
  }

  @Test
  def revokeShouldThrowWhenUsernameIsNull(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.revoke(null, LongLivedTokenFootPrint(LongLivedTokenId.generate, DEVICE_ID))).block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def revokeShouldThrowWhenLongLivedTokenFootPrintIsNull(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.revoke(USERNAME, null)).block())
      .isInstanceOf(classOf[NullPointerException])
  }

  @Test
  def revokeShouldThrowWhenLongLivedTokenFootPrintNotFound(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.revoke(USERNAME, LongLivedTokenFootPrint(LongLivedTokenId.generate, DEVICE_ID))).block())
      .isInstanceOf(classOf[LongLivedTokenNotFoundException])
  }

  @Test
  def revokeShouldRemoveAssignId(): Unit = {
    val longLivedTokenId: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LONG_LIVED_TOKEN)).block()

    SMono.fromPublisher(testee.revoke(USERNAME, LongLivedTokenFootPrint(longLivedTokenId, DEVICE_ID))).block()
    assertThat(SFlux.fromPublisher(testee.listTokens(USERNAME)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def revokeShouldNotRemoveUnAssignId(): Unit = {
    val longLivedTokenIdShouldBeRemoved: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LONG_LIVED_TOKEN)).block()
    val longLivedTokenId: LongLivedTokenId = SMono.fromPublisher(testee.store(USERNAME, LongLivedToken(DEVICE_ID, LongLivedTokenSecret.generate))).block()

    SMono.fromPublisher(testee.revoke(USERNAME, LongLivedTokenFootPrint(longLivedTokenIdShouldBeRemoved, DEVICE_ID))).block()
    assertThat(SFlux.fromPublisher(testee.listTokens(USERNAME)).collectSeq().block().asJava)
      .contains(LongLivedTokenFootPrint(longLivedTokenId, DEVICE_ID))
      .doesNotContain(LongLivedTokenFootPrint(longLivedTokenIdShouldBeRemoved, DEVICE_ID))
  }

}