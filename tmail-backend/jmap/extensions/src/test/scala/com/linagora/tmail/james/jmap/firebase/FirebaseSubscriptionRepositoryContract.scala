package com.linagora.tmail.james.jmap.firebase

import java.time.{Clock, Instant, ZoneId, ZonedDateTime}

import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionRepositoryContract.{ALICE, INVALID_EXPIRE, MAX_EXPIRE, SAMPLE_DEVICE_TOKEN_1, SAMPLE_DEVICE_TOKEN_2, VALID_EXPIRE}
import com.linagora.tmail.james.jmap.model.{DeviceClientId, DeviceClientIdInvalidException, TokenInvalidException, ExpireTimeInvalidException, FirebaseToken, FirebaseSubscription, FirebaseSubscriptionCreationRequest, FirebaseSubscriptionExpiredTime, FirebaseSubscriptionId, FirebaseSubscriptionNotFoundException}
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.{State, TypeName}
import org.apache.james.utils.UpdatableTickingClock
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

case object CustomTypeName1 extends TypeName {
  override val asString: String = "custom1"

  override def parse(string: String): Option[TypeName] = string match {
    case CustomTypeName1.asString => Some(CustomTypeName1)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, CustomState] = Right(CustomState(string))
}

case object CustomTypeName2 extends TypeName {
  override val asString: String = "custom2"

  override def parse(string: String): Option[TypeName] = string match {
    case CustomTypeName2.asString => Some(CustomTypeName2)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, CustomState] = Right(CustomState(string))
}

case class CustomState(value: String) extends State {
  override def serialize: String = value
}

object FirebaseSubscriptionRepositoryContract {
  val TYPE_NAME_SET: Set[TypeName] = Set(CustomTypeName1, CustomTypeName2)
  val NOW: Instant = Instant.parse("2022-11-01T07:05:39.160Z")
  val CLOCK: Clock = Clock.fixed(NOW, ZoneId.of("UTC"))
  val INVALID_EXPIRE: ZonedDateTime = ZonedDateTime.now(CLOCK).minusDays(10)
  val VALID_EXPIRE: ZonedDateTime = ZonedDateTime.now(CLOCK).plusDays(2)
  val MAX_EXPIRE: ZonedDateTime = ZonedDateTime.now(CLOCK).plusDays(7)
  val ALICE: Username = Username.of("alice")
  val SAMPLE_DEVICE_TOKEN_1 = FirebaseToken("dummy_device_token_1")
  val SAMPLE_DEVICE_TOKEN_2 = FirebaseToken("dummy_device_token_2")
}

trait FirebaseSubscriptionRepositoryContract {
  def clock: UpdatableTickingClock
  def testee: FirebaseSubscriptionRepository

  @Test
  def validSubscriptionShouldBeSavedSuccessfully(): Unit = {
    val validRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    val firebaseSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    val singleRecordSaved = SFlux.fromPublisher(testee.get(ALICE, Set(firebaseSubscriptionId).asJava)).count().block()

    assertThat(singleRecordSaved).isEqualTo(1)
  }

  @Test
  def subscriptionWithExpireBiggerThanMaxExpireShouldBeSetToMaxExpire(): Unit = {
    val request = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      expires = Some(FirebaseSubscriptionExpiredTime(VALID_EXPIRE.plusDays(8))),
      types = Seq(CustomTypeName1))
    val firebaseSubscriptionId = SMono.fromPublisher(testee.save(ALICE, request)).block().id
    val newSavedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(firebaseSubscriptionId).asJava)).blockFirst().get

    assertThat(newSavedSubscription.expires.value).isEqualTo(MAX_EXPIRE)
  }

  @Test
  def subscriptionWithInvalidExpireTimeShouldThrowException(): Unit = {
    val invalidRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      expires = Some(FirebaseSubscriptionExpiredTime(INVALID_EXPIRE)),
      types = Seq(CustomTypeName1))

    assertThatThrownBy(() => SMono.fromPublisher(testee.save(ALICE, invalidRequest)).block())
      .isInstanceOf(classOf[ExpireTimeInvalidException])
  }

  @Test
  def subscriptionWithDuplicatedDeviceClientIdShouldThrowException(): Unit = {
    val firstRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    SMono.fromPublisher(testee.save(ALICE, firstRequest)).block()

    val secondRequestWithDuplicatedDeviceClientId = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_2,
      types = Seq(CustomTypeName1))

    assertThatThrownBy(() => SMono.fromPublisher(testee.save(ALICE, secondRequestWithDuplicatedDeviceClientId)).block())
      .isInstanceOf(classOf[DeviceClientIdInvalidException])
  }

  @Test
  def subscriptionWithDuplicatedDeviceTokenShouldThrowException(): Unit = {
    val firstRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    SMono.fromPublisher(testee.save(ALICE, firstRequest)).block()

    val secondRequestWithDuplicatedDeviceToken = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("2"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))

    assertThatThrownBy(() => SMono.fromPublisher(testee.save(ALICE, secondRequestWithDuplicatedDeviceToken)).block())
      .isInstanceOf(classOf[TokenInvalidException])
  }

  @Test
  def updateWithOutdatedExpiresShouldThrowException(): Unit = {
    val validRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    val firebaseSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id

    assertThatThrownBy(() => SMono.fromPublisher(testee.updateExpireTime(ALICE, firebaseSubscriptionId, INVALID_EXPIRE)).block())
      .isInstanceOf(classOf[ExpireTimeInvalidException])
  }

  @Test
  def updateWithExpiresBiggerThanMaxExpiresShouldBeSetToMaxExpires(): Unit = {
    val validRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    val firebaseSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    SMono.fromPublisher(testee.updateExpireTime(ALICE, firebaseSubscriptionId, MAX_EXPIRE.plusDays(1))).block()

    val updatedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(firebaseSubscriptionId).asJava)).blockFirst().get
    assertThat(updatedSubscription.expires.value).isEqualTo(MAX_EXPIRE)
  }

  @Test
  def updateExpiresWithNotFoundSubscriptionIdShouldThrowException(): Unit = {
    val randomId = FirebaseSubscriptionId.generate()

    assertThatThrownBy(() => SMono.fromPublisher(testee.updateExpireTime(ALICE, randomId, VALID_EXPIRE)).block())
      .isInstanceOf(classOf[FirebaseSubscriptionNotFoundException])
  }

  @Test
  def updateWithValidExpiresShouldSucceed(): Unit = {
    val validRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    val firebaseSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    SMono.fromPublisher(testee.updateExpireTime(ALICE, firebaseSubscriptionId, VALID_EXPIRE)).block()

    val updatedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(firebaseSubscriptionId).asJava)).blockFirst().get
    assertThat(updatedSubscription.expires.value).isEqualTo(VALID_EXPIRE)
  }

  @Test
  def updateWithExpiresBiggerThanMaxExpiresShouldReturnServerFixedExpires(): Unit = {
    val validRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    val firebaseSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    val fixedExpires = SMono.fromPublisher(testee.updateExpireTime(ALICE, firebaseSubscriptionId, MAX_EXPIRE.plusDays(1))).block()

    assertThat(fixedExpires).isEqualTo(FirebaseSubscriptionExpiredTime(MAX_EXPIRE))
  }

  @Test
  def updateWithValidTypesShouldSucceed(): Unit = {
    val validRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    val firebaseSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id

    val newTypes: Set[TypeName] = Set(CustomTypeName1, CustomTypeName2)
    SMono.fromPublisher(testee.updateTypes(ALICE, firebaseSubscriptionId, newTypes.asJava)).block()

    val updatedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(firebaseSubscriptionId).asJava)).blockFirst().get

    assertThat(updatedSubscription.types.toSet.asJava).containsExactlyInAnyOrder(CustomTypeName1, CustomTypeName2)
  }

  @Test
  def updateTypesWithNotFoundSubscriptionShouldThrowException(): Unit = {
    val randomId = FirebaseSubscriptionId.generate()
    val newTypes: Set[TypeName] = Set(CustomTypeName1, CustomTypeName2)

    assertThatThrownBy(() => SMono.fromPublisher(testee.updateTypes(ALICE, randomId, newTypes.asJava)).block())
      .isInstanceOf(classOf[FirebaseSubscriptionNotFoundException])
  }

  @Test
  def getNotFoundSubscriptionShouldReturnEmpty(): Unit = {
    val randomId = FirebaseSubscriptionId.generate()

    assertThat(SMono.fromPublisher(testee.get(ALICE, Set(randomId).asJava)).blockOption().toJava)
      .isEmpty
  }

  @Test
  def revokeStoredSubscriptionShouldSucceed(): Unit = {
    val validRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    val firebaseSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    val singleRecordSaved = SFlux.fromPublisher(testee.get(ALICE, Set(firebaseSubscriptionId).asJava)).count().block()
    assertThat(singleRecordSaved).isEqualTo(1)

    SMono.fromPublisher(testee.revoke(ALICE, firebaseSubscriptionId)).block()
    val remainingSubscriptions = SFlux.fromPublisher(testee.get(ALICE, Set(firebaseSubscriptionId).asJava)).collectSeq().block().asJava

    assertThat(remainingSubscriptions).isEmpty()
  }

  @Test
  def revokeNotFoundShouldNotFail(): Unit = {
    val randomId = FirebaseSubscriptionId.generate()

    assertThatCode(() => SMono.fromPublisher(testee.revoke(ALICE, randomId)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def getStoredSubscriptionsShouldSucceed(): Unit = {
    val deviceClientId1 = DeviceClientId("1")
    val deviceClientId2 = DeviceClientId("2")
    val validRequest1 = FirebaseSubscriptionCreationRequest(
      deviceClientId = deviceClientId1,
      token = SAMPLE_DEVICE_TOKEN_1,
      expires = Option(FirebaseSubscriptionExpiredTime(VALID_EXPIRE)),
      types = Seq(CustomTypeName1))
    val validRequest2 = FirebaseSubscriptionCreationRequest(
      deviceClientId = deviceClientId2,
      token = SAMPLE_DEVICE_TOKEN_2,
      expires = Option(FirebaseSubscriptionExpiredTime(VALID_EXPIRE)),
      types = Seq(CustomTypeName2))
    val firebaseSubscriptionId1 = SMono.fromPublisher(testee.save(ALICE, validRequest1)).block().id
    val firebaseSubscriptionId2 = SMono.fromPublisher(testee.save(ALICE, validRequest2)).block().id

    val firebaseSubscriptions = SFlux.fromPublisher(testee.get(ALICE, Set(firebaseSubscriptionId1, firebaseSubscriptionId2).asJava)).collectSeq().block()

    assertThat(firebaseSubscriptions.map(_.id).toList.asJava).containsExactlyInAnyOrder(firebaseSubscriptionId1, firebaseSubscriptionId2)
  }

  @Test
  def getWhenMixedAvailableAndNotFoundSubscriptionsShouldReturnOnlyAvailableSubscription(): Unit = {
    val deviceClientId1 = DeviceClientId("1")
    val validRequest1 = FirebaseSubscriptionCreationRequest(
      deviceClientId = deviceClientId1,
      token = SAMPLE_DEVICE_TOKEN_1,
      expires = Option(FirebaseSubscriptionExpiredTime(VALID_EXPIRE)),
      types = Seq(CustomTypeName1))
    val firebaseSubscriptionId1 = SMono.fromPublisher(testee.save(ALICE, validRequest1)).block().id
    val firebaseSubscriptionId2 = FirebaseSubscriptionId.generate()

    val firebaseSubscriptions = SFlux.fromPublisher(testee.get(ALICE, Set(firebaseSubscriptionId1, firebaseSubscriptionId2).asJava)).collectSeq().block()

    assertThat(firebaseSubscriptions.map(_.id).toList.asJava).containsExactlyInAnyOrder(firebaseSubscriptionId1)
  }

  @Test
  def getSubscriptionsShouldNotReturnOutdatedSubscription(): Unit = {
    val deviceClientId1 = DeviceClientId("1")
    val deviceClientId2 = DeviceClientId("2")
    val validRequest1 = FirebaseSubscriptionCreationRequest(
      deviceClientId = deviceClientId1,
      token = SAMPLE_DEVICE_TOKEN_1,
      expires = Option(FirebaseSubscriptionExpiredTime(VALID_EXPIRE.plusDays(1))),
      types = Seq(CustomTypeName1))
    val validRequest2 = FirebaseSubscriptionCreationRequest(
      deviceClientId = deviceClientId2,
      token = SAMPLE_DEVICE_TOKEN_2,
      expires = Option(FirebaseSubscriptionExpiredTime(VALID_EXPIRE.plusDays(3))),
      types = Seq(CustomTypeName2))
    val outdatedSubscriptionId1 = SMono.fromPublisher(testee.save(ALICE, validRequest1)).block().id
    val nonOutdatedSubscriptionId2 = SMono.fromPublisher(testee.save(ALICE, validRequest2)).block().id

    clock.setInstant(VALID_EXPIRE.plusDays(2).toInstant)

    val subscriptions = SFlux.fromPublisher(testee.get(ALICE, Set(outdatedSubscriptionId1, nonOutdatedSubscriptionId2).asJava)).collectSeq().block()

    assertThat(subscriptions.map(_.id).toList.asJava).containsOnly(nonOutdatedSubscriptionId2)
  }

  @Test
  def listStoredSubscriptionShouldSucceed(): Unit = {
    val deviceClientId1 = DeviceClientId("1")
    val deviceClientId2 = DeviceClientId("2")
    val validRequest1 = FirebaseSubscriptionCreationRequest(
      deviceClientId = deviceClientId1,
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    val validRequest2 = FirebaseSubscriptionCreationRequest(
      deviceClientId = deviceClientId2,
      token = SAMPLE_DEVICE_TOKEN_2,
      types = Seq(CustomTypeName2))
    val firebaseSubscriptionId1 = SMono.fromPublisher(testee.save(ALICE, validRequest1)).block().id
    val firebaseSubscriptionId2 = SMono.fromPublisher(testee.save(ALICE, validRequest2)).block().id

    val subscriptions: List[FirebaseSubscription] = SFlux(testee.list(ALICE)).collectSeq().block().toList

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(subscriptions.map(_.id).asJava).containsOnly(firebaseSubscriptionId1, firebaseSubscriptionId2)
      softly.assertThat(subscriptions.map(_.deviceClientId).asJava).containsExactlyInAnyOrder(deviceClientId1, deviceClientId2)
    })
  }

  @Test
  def listSubscriptionShouldNotReturnOutdatedSubscriptions(): Unit = {
    val deviceClientId1 = DeviceClientId("1")
    val deviceClientId2 = DeviceClientId("2")
    val validRequest1 = FirebaseSubscriptionCreationRequest(
      deviceClientId = deviceClientId1,
      token = SAMPLE_DEVICE_TOKEN_1,
      expires = Option(FirebaseSubscriptionExpiredTime(VALID_EXPIRE.plusDays(1))),
      types = Seq(CustomTypeName1))
    val validRequest2 = FirebaseSubscriptionCreationRequest(
      deviceClientId = deviceClientId2,
      token = SAMPLE_DEVICE_TOKEN_2,
      expires = Option(FirebaseSubscriptionExpiredTime(VALID_EXPIRE.plusDays(3))),
      types = Seq(CustomTypeName2))
    SMono.fromPublisher(testee.save(ALICE, validRequest1)).block().id
    val nonOutdatedSubscription = SMono.fromPublisher(testee.save(ALICE, validRequest2)).block().id

    clock.setInstant(VALID_EXPIRE.plusDays(2).toInstant)

    val subscriptions = SFlux.fromPublisher(testee.list(ALICE)).collectSeq().block()

    assertThat(subscriptions.map(_.id).toList.asJava).containsOnly(nonOutdatedSubscription)
  }

}
