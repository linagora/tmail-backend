package com.linagora.tmail.james.common

import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import com.google.firebase.messaging.{FirebaseMessagingException, MessagingErrorCode}
import com.linagora.tmail.james.common.FirebasePushContract.firebasePushClient
import com.linagora.tmail.james.common.FirebaseSubscriptionGetMethodContract.TIME_FORMATTER
import com.linagora.tmail.james.jmap.firebase.{FirebasePushClient, FirebasePushRequest, FirebasePushUrgency}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.utils.{DataProbeImpl, SMTPMessageSender, SpoolerProbe, UpdatableTickingClock}
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{clearInvocations, mock, times, verify, verifyNoMoreInteractions, when}
import reactor.core.publisher.Mono

object FirebasePushContract {
  val firebasePushClient: FirebasePushClient = mock(classOf[FirebasePushClient])
}

trait FirebasePushContract {

  private lazy val awaitAtMostTenSeconds: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
    .atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    when(firebasePushClient.validateToken(any())).thenReturn(Mono.just(true))
    when(firebasePushClient.push(any())).thenReturn(Mono.empty())
  }

  @AfterEach
  def teardown(): Unit = clearInvocations(firebasePushClient)

  private def createFirebaseSubscription(deviceClientId: String = "deviceClient1", fcmToken: String = "token1"): String =
    `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "create": {
           |                "4f29": {
           |                  "deviceClientId": "$deviceClientId",
           |                  "token": "$fcmToken",
           |                  "types": ["Mailbox", "EmailDelivery"]
           |                }
           |              }
           |        },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.4f29.id")

  private def sendEmailToBob(server: GuiceJamesServer): Unit = {
    val smtpMessageSender: SMTPMessageSender = new SMTPMessageSender(DOMAIN.asString())
    smtpMessageSender.connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(ANDRE.asString, ANDRE_PASSWORD)
      .sendMessage(ANDRE.asString, BOB.asString())
    smtpMessageSender.close()

    awaitAtMostTenSeconds.until(() => server.getProbe(classOf[SpoolerProbe]).processingFinished())
  }

  @Test
  def shouldPushStateChangeWhenValidTokenAndReceiveMail(server: GuiceJamesServer): Unit = {
    // bob creates a firebase subscription
    createFirebaseSubscription()
    verify(firebasePushClient).validateToken(any())

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob should receive a state change notification from firebase
    awaitAtMostTenSeconds.untilAsserted(() => verify(firebasePushClient).push(any()))
  }

  @Test
  def shouldNotPushStateChangeWhenValidTokenAndNoChange(): Unit = {
    // bob creates a firebase subscription
    createFirebaseSubscription()
    verify(firebasePushClient).validateToken(any())

    // WHEN bob has no change

    // THEN bob should not receive a state change notification from firebase
    awaitAtMostTenSeconds.untilAsserted(() => verifyNoMoreInteractions(firebasePushClient))
  }

  @Test
  def shouldPushStateChangeWhenNotExpiredSubscription(server: GuiceJamesServer, clock: UpdatableTickingClock): Unit = {
    // bob creates a firebase subscription
    createFirebaseSubscription()
    verify(firebasePushClient).validateToken(any())

    // GIVEN 3 days passed
    clock.setInstant(clock.instant().plus(3, ChronoUnit.DAYS))

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob should receive a state change notification from firebase
    awaitAtMostTenSeconds.untilAsserted(() => verify(firebasePushClient).push(any()))
  }

  @Test
  def shouldNotPushStateChangeWhenExpiredSubscription(server: GuiceJamesServer, clock: UpdatableTickingClock): Unit = {
    // bob creates a firebase subscription
    createFirebaseSubscription()
    verify(firebasePushClient).validateToken(any())

    // GIVEN 8 days passed
    clock.setInstant(clock.instant().plus(8, ChronoUnit.DAYS))

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob should not receive a state change notification from firebase
    awaitAtMostTenSeconds.untilAsserted(() => verifyNoMoreInteractions(firebasePushClient))
  }

  @Test
  def shouldNotPushStateChangeWhenDestroyedSubscription(server: GuiceJamesServer): Unit = {
    // bob creates a firebase subscription
    val subscriptionId: String = createFirebaseSubscription()
    verify(firebasePushClient).validateToken(any())

    // AND he destroy the subscription
    `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"
           |  ],
           |  "methodCalls": [
           |    [
           |      "FirebaseRegistration/set",
           |      {
           |        "destroy": [
           |          "$subscriptionId"
           |        ]
           |      },
           |      "c1"
           |    ]
           |  ]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob should not receive a state change notification from firebase
    awaitAtMostTenSeconds.untilAsserted(() => verifyNoMoreInteractions(firebasePushClient))
  }

  @Test
  def shouldPushStateChangeWhenExtendedExpireSubscription(server: GuiceJamesServer, clock: UpdatableTickingClock): Unit = {
    // bob creates a firebase subscription
    val subscriptionId: String = createFirebaseSubscription()
    verify(firebasePushClient).validateToken(any())

    // And 3 days passed and Bob extends his subscription expire for 6 more days
    clock.setInstant(clock.instant().plus(3, ChronoUnit.DAYS))
    val newExpires = clock.instant().plus(6, ChronoUnit.DAYS).atZone(ZoneId.of("UTC")).format(TIME_FORMATTER)
    `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "$subscriptionId": {
           |                  "expires": "$newExpires"
           |                }
           |              }
           |        },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    // WHEN bob receives a mail 5 days later
    clock.setInstant(clock.instant().plus(5, ChronoUnit.DAYS))
    sendEmailToBob(server)

    // THEN bob should receive a state change notification from firebase
    awaitAtMostTenSeconds.untilAsserted(() => verify(firebasePushClient).push(any()))
  }

  @Test
  def shouldPushStateChangeWhenManySubscriptions(server: GuiceJamesServer): Unit = {
    // bob creates firebase subscriptions for client A and client B
    createFirebaseSubscription(deviceClientId = "clientA", fcmToken = "tokenA")
    createFirebaseSubscription(deviceClientId = "clientB", fcmToken = "tokenB")
    verify(firebasePushClient, times(2)).validateToken(any())

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob should receive state change notifications for both clients
    awaitAtMostTenSeconds.untilAsserted(() => verify(firebasePushClient, times(2)).push(any()))
  }

  @Test
  def shouldRemoveSubscriptionWhenUnregisteredToken(server: GuiceJamesServer): Unit = {
    // bob creates a firebase subscription
    val subscriptionId = createFirebaseSubscription()
    verify(firebasePushClient).validateToken(any())

    // WHEN FCM invalidate the token and bob receives a mail
    val firebaseException = mock(classOf[FirebaseMessagingException])
    when(firebaseException.getMessagingErrorCode).thenReturn(MessagingErrorCode.UNREGISTERED)
    when(firebasePushClient.push(any())).thenReturn(Mono.error(firebaseException))

    sendEmailToBob(server)

    // THEN the subscription should be removed
    awaitAtMostTenSeconds.untilAsserted(() => {
      val response = `given`
        .body(
          s"""{
             |  "using": [
             |    "urn:ietf:params:jmap:core",
             |    "com:linagora:params:jmap:firebase:push"],
             |  "methodCalls": [[
             |    "FirebaseRegistration/get",
             |    {
             |      "accountId": "$ACCOUNT_ID",
             |      "ids": ["$subscriptionId"]
             |    },
             |    "c1"]]
             |}""".stripMargin)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "FirebaseRegistration/get",
           |            {
           |                "notFound": ["$subscriptionId"],
           |                "list": []
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    })
  }

  @Test
  def shouldNotRemoveSubscriptionWhenUnexpectedFcmError(server: GuiceJamesServer): Unit = {
    // bob creates a firebase subscription
    val subscriptionId = createFirebaseSubscription()
    verify(firebasePushClient).validateToken(any())

    // WHEN FCM is down and bob receives a mail
    val firebaseException = mock(classOf[FirebaseMessagingException])
    when(firebaseException.getMessagingErrorCode).thenReturn(MessagingErrorCode.INTERNAL)
    when(firebasePushClient.push(any())).thenReturn(Mono.error(firebaseException))

    sendEmailToBob(server)

    // THEN the subscription should not be removed
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "ids": ["$subscriptionId"]
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/get",
         |            {
         |                "notFound": [],
         |                "list": [
         |                    {
         |                        "id": "$subscriptionId",
         |                        "deviceClientId": "$${json-unit.ignore}",
         |                        "expires": "$${json-unit.ignore}",
         |                        "types": [
         |                            "Mailbox",
         |                            "EmailDelivery"
         |                        ]
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def shouldPushWithHighPriorityWhenChangeHasEmailDelivery(server: GuiceJamesServer): Unit = {
    // bob creates a firebase subscription
    createFirebaseSubscription()
    verify(firebasePushClient).validateToken(any())

    // WHEN bob receives a mail
    sendEmailToBob(server)

    // THEN bob should receive state change notification with high priority
    awaitAtMostTenSeconds.untilAsserted(() => {
      val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
      verify(firebasePushClient).push(argumentCaptor.capture())
      assertThat(argumentCaptor.getValue.urgency().toString).isEqualTo(FirebasePushUrgency.HIGH.toString)
    })
  }

  @Test
  def shouldPushWithNormalPriorityWhenChangeDoesNotHaveEmailDelivery(server: GuiceJamesServer): Unit = {
    // bob creates a firebase subscription
    createFirebaseSubscription()
    verify(firebasePushClient).validateToken(any())

    // WHEN bob create a new mailbox
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "whatever"))

    // THEN bob should receive state change notification with normal priority
    awaitAtMostTenSeconds.untilAsserted(() => {
      val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
      verify(firebasePushClient).push(argumentCaptor.capture())
      assertThat(argumentCaptor.getValue.urgency().toString).isEqualTo(FirebasePushUrgency.NORMAL.toString)
    })
  }
}
