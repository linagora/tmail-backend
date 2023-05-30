package com.linagora.tmail.james.jmap.firebase

import com.linagora.tmail.james.jmap.contact.ContactUsernameChangeTaskStepTest.ALICE
import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionRepositoryContract.{BOB, SAMPLE_DEVICE_TOKEN_1, SAMPLE_DEVICE_TOKEN_2}
import com.linagora.tmail.james.jmap.model.{DeviceClientId, FirebaseSubscriptionCreationRequest}
import org.apache.james.utils.UpdatableTickingClock
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class FirebaseSubscriptionUserDeletionTaskStepTest {
  var firebaseSubscriptionRepository: FirebaseSubscriptionRepository = _
  var testee: FirebaseSubscriptionUserDeletionTaskStep = _

  @BeforeEach
  def beforeEach(): Unit = {
    firebaseSubscriptionRepository = new MemoryFirebaseSubscriptionRepository(new UpdatableTickingClock(FirebaseSubscriptionRepositoryContract.NOW))
    testee = new FirebaseSubscriptionUserDeletionTaskStep(firebaseSubscriptionRepository)
  }

  @Test
  def shouldRemoveSubscriptions(): Unit = {
    val validRequest1 = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    val validRequest2 = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("2"),
      token = SAMPLE_DEVICE_TOKEN_2,
      types = Seq(CustomTypeName1))
    SMono.fromPublisher(firebaseSubscriptionRepository.save(ALICE, validRequest1)).block()
    SMono.fromPublisher(firebaseSubscriptionRepository.save(ALICE, validRequest2)).block()

    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux.fromPublisher(firebaseSubscriptionRepository.list(ALICE))
      .collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def shouldNotRemoveSubscriptionsOfOtherUsers(): Unit = {
    val validRequest1 = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      token = SAMPLE_DEVICE_TOKEN_1,
      types = Seq(CustomTypeName1))
    val validRequest2 = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("2"),
      token = SAMPLE_DEVICE_TOKEN_2,
      types = Seq(CustomTypeName1))
    SMono.fromPublisher(firebaseSubscriptionRepository.save(ALICE, validRequest1)).block()
    val bobSubscription = SMono.fromPublisher(firebaseSubscriptionRepository.save(BOB, validRequest2)).block()

    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux.fromPublisher(firebaseSubscriptionRepository.list(BOB))
      .collectSeq().block().asJava)
      .containsOnly(bobSubscription)
  }

  @Test
  def shouldBeIdempotent(): Unit = {
    SMono.fromPublisher(testee.deleteUserData(ALICE)).block()

    assertThatCode(() => SMono.fromPublisher(testee.deleteUserData(ALICE)).block())
      .doesNotThrowAnyException()
  }

}
