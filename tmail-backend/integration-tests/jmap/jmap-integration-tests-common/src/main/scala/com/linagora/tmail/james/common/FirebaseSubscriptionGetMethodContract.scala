package com.linagora.tmail.james.common

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.google.common.collect.ImmutableSet
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.common.FirebaseSubscriptionGetMethodContract.{FIREBASE_SUBSCRIPTION_CREATE_REQUEST, TIME_FORMATTER}
import com.linagora.tmail.james.jmap.firebase.{FirebasePushClient, FirebaseSubscriptionRepository}
import com.linagora.tmail.james.jmap.model.{DeviceClientId, FirebaseSubscription, FirebaseSubscriptionCreationRequest, FirebaseSubscriptionExpiredTime, FirebaseSubscriptionId, FirebaseToken}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import jakarta.inject.Inject
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.change.MailboxTypeName
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.Mockito.mock
import reactor.core.scala.publisher.SMono

class FirebaseSubscriptionProbe @Inject()(firebaseSubscriptionRepository: FirebaseSubscriptionRepository) extends GuiceProbe {
  def createSubscription(username: Username, request: FirebaseSubscriptionCreationRequest): FirebaseSubscription =
    SMono(firebaseSubscriptionRepository.save(username, request))
      .block()

  def retrieveSubscription(username: Username, id: FirebaseSubscriptionId): FirebaseSubscription =
    SMono(firebaseSubscriptionRepository.get(username, ImmutableSet.of(id))).block()
}

class FirebaseSubscriptionProbeModule extends AbstractModule {

  override def configure(): Unit =
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[FirebaseSubscriptionProbe])
}

object FirebaseSubscriptionGetMethodContract {
  val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
  val FIREBASE_SUBSCRIPTION_CREATE_REQUEST: FirebaseSubscriptionCreationRequest = FirebaseSubscriptionCreationRequest(
    deviceClientId = DeviceClientId("ipad gen 9"),
    token = FirebaseToken("fire-base-token-2"),
    expires = Some(FirebaseSubscriptionExpiredTime(UTCDate(ZonedDateTime.now().plusDays(1)).asUTC)),
    types = Seq(MailboxTypeName))
  val firebasePushClient: FirebasePushClient = mock(classOf[FirebasePushClient])
}

trait FirebaseSubscriptionGetMethodContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def getShouldReturnEmptyWhenHaveNotSubscription(): Unit = {
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
           |      "ids": null
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "FirebaseRegistration/get",
         |    {
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnEntryWhenHaveSubscription(server: GuiceJamesServer): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

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
           |      "ids": null
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
         |                "notFound": [],
         |                "list": [
         |                    {
         |                        "expires": "${FIREBASE_SUBSCRIPTION_CREATE_REQUEST.expires.get.value.format(TIME_FORMATTER)}",
         |                        "id": "${firebaseSubscription.id.value.toString}",
         |                        "deviceClientId": "ipad gen 9",
         |                        "types": ["Mailbox"]
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnEntriesWhenHaveSeveralSubscription(server: GuiceJamesServer): Unit = {
    val createRequest2: FirebaseSubscriptionCreationRequest = FIREBASE_SUBSCRIPTION_CREATE_REQUEST.copy(
      deviceClientId = DeviceClientId("ipad gen 10"),
      token = FirebaseToken("fire-base-token-3"))

    val firebaseSubscription1 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)
    val firebaseSubscription2 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, createRequest2)

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
           |      "ids": null
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
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
           |                        "expires": "${FIREBASE_SUBSCRIPTION_CREATE_REQUEST.expires.get.value.format(TIME_FORMATTER)}",
           |                        "id": "${firebaseSubscription1.id.value.toString}",
           |                        "deviceClientId": "ipad gen 9",
           |                        "types": ["Mailbox"]
           |                    },
           |                    {
           |                        "expires": "${createRequest2.expires.get.value.format(TIME_FORMATTER)}",
           |                        "id": "${firebaseSubscription2.id.value.toString}",
           |                        "deviceClientId": "ipad gen 10",
           |                        "types": ["Mailbox"]
           |                    }
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def getShouldReturnEmptyListWhenIdsAreEmpty(server: GuiceJamesServer): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

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
           |      "ids": []
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
         |                "notFound": [],
         |                "list": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnNotFoundWhenIdDoesNotExist(server: GuiceJamesServer): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

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
           |      "ids": ["notFound1"]
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
         |                "notFound": ["notFound1"],
         |                "list": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnNotFoundAndListWhenMixCases(server: GuiceJamesServer): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

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
           |      "ids": ["notFound1", "${firebaseSubscription.id.value}"]
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
         |                "notFound": ["notFound1"],
         |                "list": [
         |                    {
         |                        "expires": "${FIREBASE_SUBSCRIPTION_CREATE_REQUEST.expires.get.value.format(TIME_FORMATTER)}",
         |                        "id": "${firebaseSubscription.id.value.toString}",
         |                        "deviceClientId": "ipad gen 9",
         |                        "types": ["Mailbox"]
         |                    }
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def getShouldNotReturnSubscriptionOfOtherAccount(server: GuiceJamesServer): Unit = {
    val andreFirebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(ANDRE, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

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
           |      "ids": null
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
         |                "notFound": [],
         |                "list": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnNotFoundWhenDoesNotHavePermission(server: GuiceJamesServer): Unit = {
    val andreFirebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(ANDRE, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

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
           |      "ids": ["${andreFirebaseSubscription.id.value}"]
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
         |                "notFound": ["${andreFirebaseSubscription.id.value}"],
         |                "list": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnOnlyRequestedProperties(server: GuiceJamesServer): Unit = {
    val andreFirebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

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
           |      "ids": null,
           |      "properties": ["deviceClientId","types"]
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
         |                "notFound": [],
         |                "list": [
         |                    {
         |                        "id": "${andreFirebaseSubscription.id.value}",
         |                        "deviceClientId": "ipad gen 9",
         |                        "types": [
         |                            "Mailbox"
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
  def getShouldFailWhenInvalidProperties(server: GuiceJamesServer): Unit = {
    val andreFirebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

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
           |      "ids": null,
           |      "properties": ["token"]
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
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "The following properties [token] do not exist."
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def getShouldFailWhenInvalidIds(server: GuiceJamesServer): Unit = {
    val andreFirebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

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
           |      "ids": ["#==id"]
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
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "$${json-unit.any-string}"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def getShouldFailWhenOmittingOneCapability(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "ids": null
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): com:linagora:params:jmap:firebase:push"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def getShouldFailWhenOmittingAllCapability(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "ids": null
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:jmap:firebase:push"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }
}
