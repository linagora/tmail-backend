/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.firebase

import java.time.Clock
import java.util.UUID

import com.google.common.collect.ImmutableList
import com.google.firebase.messaging.{FirebaseMessagingException, MessagingErrorCode}
import com.linagora.tmail.james.jmap.model.{DeviceClientId, FirebaseSubscriptionCreationRequest, FirebaseToken}
import com.linagora.tmail.james.jmap.settings.{JmapSettingsKey, JmapSettingsRepository, JmapSettingsUpsertRequest, JmapSettingsValue, MemoryJmapSettingsRepository}
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.change.{EmailDeliveryTypeName, EmailTypeName, MailboxTypeName, StateChangeEvent}
import org.apache.james.jmap.core.UuidState
import org.apache.james.user.api.DelegationStore
import org.apache.james.user.memory.MemoryDelegationStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, times, verify, verifyNoInteractions, when}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono

import scala.language.postfixOps

class FirebasePushListenerTest {
  val bob: Username = Username.of("bob@localhost")
  val alice: Username = Username.of("alice@localhost")
  val bobAccountId: String = "405010d6c16c16dec36f3d7a7596c2d757ba7b57904adc4801a63e40914fd5c9"

  var testee: FirebasePushListener = _
  var subscriptionRepository: FirebaseSubscriptionRepository = _
  var pushClient: FirebasePushClient = _
  var delegationStore: DelegationStore = _
  var jmapSettingsRepository: JmapSettingsRepository = _

  @BeforeEach
  def setUp(): Unit = {
    subscriptionRepository = new MemoryFirebaseSubscriptionRepository(Clock.systemUTC())
    pushClient = mock(classOf[FirebasePushClient])
    delegationStore = new MemoryDelegationStore()
    jmapSettingsRepository = MemoryJmapSettingsRepository()
    testee = new FirebasePushListener(subscriptionRepository, delegationStore, jmapSettingsRepository, pushClient, Clock.systemUTC())

    when(pushClient.push(any())).thenReturn(Mono.empty)
  }

  private def setFirebasePushEnabled(username: Username, pushSettingValue: String): Unit =
    SMono(jmapSettingsRepository.reset(username,
      JmapSettingsUpsertRequest(Map(JmapSettingsKey.liftOrThrow("firebase.enabled") -> JmapSettingsValue(pushSettingValue)))))
      .block()

  @Test
  def shouldNotPushWhenNoSubscriptions(): Unit = {
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID()))))).block()

    verifyNoInteractions(pushClient)
  }

  @Test
  def shouldNotPushWhenTypeMismatch(): Unit = {
    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailDeliveryTypeName)))).block().id

    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID()))))).block()

    verifyNoInteractions(pushClient)
  }

  @Test
  def shouldPushWhenMatchTypes(): Unit = {
    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailTypeName)))).block().id

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1)))).block()

    val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
    verify(pushClient).push(argumentCaptor.capture())

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(argumentCaptor.getValue.token().value).isEqualTo(FirebaseToken("token").value)
      softly.assertThat(argumentCaptor.getValue.stateChangesMap())
        .isEqualTo(java.util.Map.of(s"$bobAccountId:${EmailTypeName.asString}", s"${state1.value.toString}"))
    })
  }

  @Test
  def unwantedTypesShouldBeFilteredOut(): Unit = {
    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailTypeName, MailboxTypeName)))).block().id

    val state1 = UuidState(UUID.randomUUID())
    val state2 = UuidState(UUID.randomUUID())
    val state3 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1, MailboxTypeName -> state2, EmailDeliveryTypeName -> state3)))).block()

    val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
    verify(pushClient).push(argumentCaptor.capture())

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(argumentCaptor.getValue.token().value).isEqualTo(FirebaseToken("token").value)
      softly.assertThat(argumentCaptor.getValue.stateChangesMap())
        .isEqualTo(java.util.Map.of(s"$bobAccountId:${EmailTypeName.asString}", s"${state1.value.toString}",
          s"$bobAccountId:${MailboxTypeName.asString}", s"${state2.value.toString}"))
    })
  }

  @Test
  def shouldRemoveSubscriptionWhenInvalidToken(): Unit = {
    val firebaseException = mock(classOf[FirebaseMessagingException])
    when(firebaseException.getMessagingErrorCode).thenReturn(MessagingErrorCode.INVALID_ARGUMENT)
    when(pushClient.push(any())).thenReturn(Mono.error(firebaseException))

    val subscriptionId = SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("invalid_token"),
      types = Seq(EmailTypeName)))).block().id

    assertThat(SMono.fromPublisher(subscriptionRepository.get(bob, java.util.Set.of(subscriptionId))).block())
      .isNotNull

    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID()))))).block()

    assertThat(SMono.fromPublisher(subscriptionRepository.get(bob, java.util.Set.of(subscriptionId))).block())
      .isNull()
  }

  @Test
  def shouldRemoveSubscriptionWhenUnregisteredToken(): Unit = {
    val firebaseException = mock(classOf[FirebaseMessagingException])
    when(firebaseException.getMessagingErrorCode).thenReturn(MessagingErrorCode.UNREGISTERED)
    when(pushClient.push(any())).thenReturn(Mono.error(firebaseException))

    val subscriptionId = SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailTypeName)))).block().id

    assertThat(SMono.fromPublisher(subscriptionRepository.get(bob, java.util.Set.of(subscriptionId))).block())
      .isNotNull

    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID()))))).block()

    assertThat(SMono.fromPublisher(subscriptionRepository.get(bob, java.util.Set.of(subscriptionId))).block())
      .isNull()
  }

  @Test
  def shouldNotRemoveSubscriptionWhenFcmIsUnavailable(): Unit = {
    val firebaseException = mock(classOf[FirebaseMessagingException])
    when(firebaseException.getMessagingErrorCode).thenReturn(MessagingErrorCode.UNAVAILABLE)
    when(pushClient.push(any())).thenReturn(Mono.error(firebaseException))

    val subscriptionId = SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailTypeName)))).block().id

    assertThat(SMono.fromPublisher(subscriptionRepository.get(bob, java.util.Set.of(subscriptionId))).block())
      .isNotNull

    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID())))))
      .onErrorResume(_ => SMono.empty).block()

    assertThat(SMono.fromPublisher(subscriptionRepository.get(bob, java.util.Set.of(subscriptionId))).block())
      .isNotNull
  }

  @Test
  def stateChangeEventHasEmailDeliveryAndUserSubscribesToItThenUrgencyShouldBeHigh(): Unit = {
    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID()), MailboxTypeName -> UuidState(UUID.randomUUID()), EmailDeliveryTypeName -> UuidState(UUID.randomUUID())))))
      .block()

    val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
    verify(pushClient).push(argumentCaptor.capture())

    assertThat(argumentCaptor.getValue.urgency().toString).isEqualTo(FirebasePushUrgency.HIGH.toString)
  }

  @Test
  def stateChangeEventHasEmailDeliveryAndUserDoesNotSubscribeToItThenUrgencyShouldBeNormal(): Unit = {
    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailTypeName)))).block().id

    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID()), MailboxTypeName -> UuidState(UUID.randomUUID()), EmailDeliveryTypeName -> UuidState(UUID.randomUUID())))))
      .block()

    val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
    verify(pushClient).push(argumentCaptor.capture())

    assertThat(argumentCaptor.getValue.urgency().toString).isEqualTo(FirebasePushUrgency.NORMAL.toString)
  }

  @Test
  def stateChangeEventHasNoEmailDeliveryThenUrgencyShouldBeNormal(): Unit = {
    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID()), MailboxTypeName -> UuidState(UUID.randomUUID()))))).block()

    val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
    verify(pushClient).push(argumentCaptor.capture())

    assertThat(argumentCaptor.getValue.urgency().toString).isEqualTo(FirebasePushUrgency.NORMAL.toString)
  }

  @Test
  def shouldNotPushAliceChangesToBobWhenBobIsNotDelegatedByAlice(): Unit = {
    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), alice, Map(EmailTypeName -> UuidState(UUID.randomUUID()))))).block()

    verify(pushClient, times(0)).push(any())
  }

  @Test
  def shouldPushAliceChangesToAliceAndBobWhenBobIsDelegatedByAlice(): Unit = {
    SMono.fromPublisher(delegationStore.addAuthorizedUser(alice, bob)).block()

    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token1"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    SMono(subscriptionRepository.save(alice, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit2"),
      token = FirebaseToken("token2"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), alice, Map(EmailTypeName -> state1)))).block()

    val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
    verify(pushClient, times(2)).push(argumentCaptor.capture())

    assertThat(argumentCaptor.getAllValues
      .stream()
      .map(_.token().value)
      .collect(ImmutableList.toImmutableList[String]))
      .containsExactlyInAnyOrder("token1", "token2")
  }

  @Test
  def bobShouldReceiveHisChangesAndAliceChangesWhenBobIsDelegatedByAlice(): Unit = {
    SMono.fromPublisher(delegationStore.addAuthorizedUser(alice, bob)).block()

    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token1"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    SMono(subscriptionRepository.save(alice, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit2"),
      token = FirebaseToken("token2"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    val stateChangeBob = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob, Map(EmailTypeName -> stateChangeBob)))).block()
    val stateChangeAlice = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), alice, Map(EmailTypeName -> stateChangeAlice)))).block()

    val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
    verify(pushClient, times(3)).push(argumentCaptor.capture())

    assertThat(argumentCaptor.getAllValues
      .stream()
      .map(_.token().value)
      .collect(ImmutableList.toImmutableList[String]))
      .containsExactlyInAnyOrder("token1", "token1", "token2")
  }

  @Test
  def shouldPushWithDefaultFirebasePushSetting(): Unit = {
    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailTypeName)))).block().id

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1)))).block()

    verify(pushClient, times(1)).push(any())
  }

  @Test
  def shouldPushWhenFirebasePushSettingEnabled(): Unit = {
    setFirebasePushEnabled(bob, "true")

    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token"),
      types = Seq(EmailTypeName)))).block().id

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1)))).block()

    verify(pushClient, times(1)).push(any())
  }

  @Test
  def shouldNotPushToBothAccountOwnerAndDelegateesWhenAccountOwnerDisablesFirebasePushSetting(): Unit = {
    // Bob delegates Alice to access his account
    SMono.fromPublisher(delegationStore.addAuthorizedUser(bob, alice)).block()

    // Bob as account owner do not want his data to be transited via FCM
    setFirebasePushEnabled(bob, "false")

    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token1"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    SMono(subscriptionRepository.save(alice, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit2"),
      token = FirebaseToken("token2"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1)))).block()

    // Then Bob data should not be pushed via FCM to both Bob and Alice
    verify(pushClient, times(0)).push(any())
  }

  @Test
  def shouldPushToBothAccountOwnerAndDelegateesWhenBothAccountOwnerAndDelegateesEnablesFirebasePush(): Unit = {
    // Bob delegates Alice to access his account
    SMono.fromPublisher(delegationStore.addAuthorizedUser(bob, alice)).block()

    setFirebasePushEnabled(bob, "true")
    setFirebasePushEnabled(alice, "true")

    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("token1"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    SMono(subscriptionRepository.save(alice, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit2"),
      token = FirebaseToken("token2"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1)))).block()

    // Then both Bob and Alice should receive notification from FCM
    verify(pushClient, times(2)).push(any())
  }

  @Test
  def shouldPushToAccountOwnerWhenOnlyDelegateesDisableFirebasePush(): Unit = {
    // Bob delegates Alice to access his account
    SMono.fromPublisher(delegationStore.addAuthorizedUser(bob, alice)).block()

    setFirebasePushEnabled(bob, "true")
    setFirebasePushEnabled(alice, "false")

    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("bobToken"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    SMono(subscriptionRepository.save(alice, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit2"),
      token = FirebaseToken("aliceToken"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1)))).block()

    // Then Bob should receive notification from FCM
    val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
    verify(pushClient, times(1)).push(argumentCaptor.capture())
    assertThat(argumentCaptor.getValue.token().value).isEqualTo("bobToken")
  }

  @Test
  def shouldNotPushToDelegateesWhenDelegateesDisableFirebasePush(): Unit = {
    // Bob delegates Alice to access his account
    SMono.fromPublisher(delegationStore.addAuthorizedUser(bob, alice)).block()

    setFirebasePushEnabled(bob, "true")
    setFirebasePushEnabled(alice, "false")

    SMono(subscriptionRepository.save(bob, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      token = FirebaseToken("bobToken"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    SMono(subscriptionRepository.save(alice, FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit2"),
      token = FirebaseToken("aliceToken"),
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1)))).block()

    // Then Alice should not receive notification from FCM
    val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
    verify(pushClient, times(1)).push(argumentCaptor.capture())
    assertThat(argumentCaptor.getValue.token().value).isNotEqualTo("aliceToken")
  }
}

