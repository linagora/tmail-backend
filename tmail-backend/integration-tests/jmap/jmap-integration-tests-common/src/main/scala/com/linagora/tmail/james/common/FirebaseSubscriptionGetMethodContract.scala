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

package com.linagora.tmail.james.common

import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZonedDateTime}
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import com.google.common.collect.ImmutableSet
import com.google.common.hash.Hashing
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.common.FirebaseSubscriptionGetMethodContract.TIME_FORMATTER
import com.linagora.tmail.james.jmap.firebase.{FirebasePushClient, FirebaseSubscriptionRepository}
import com.linagora.tmail.james.jmap.model.{DeviceClientId, FirebaseSubscription, FirebaseSubscriptionCreationRequest, FirebaseSubscriptionExpiredTime, FirebaseSubscriptionId, FirebaseToken}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import jakarta.inject.Inject
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.change.MailboxTypeName
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.{DataProbeImpl, GuiceProbe, UpdatableTickingClock}
import org.assertj.core.api.Assertions.assertThat
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
  case class TestContext(bobUsername: Username,
                         bobAccountId: String,
                         andreUsername: Username,
                         firebaseSubscriptionCreateRequest: FirebaseSubscriptionCreationRequest)

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
  val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
  val FIREBASE_SUBSCRIPTION_CREATE_REQUEST: FirebaseSubscriptionCreationRequest = FirebaseSubscriptionCreationRequest(
    deviceClientId = DeviceClientId("ipad gen 9"),
    token = FirebaseToken("fire-base-token-2"),
    expires = Some(FirebaseSubscriptionExpiredTime(UTCDate(ZonedDateTime.now().plusDays(1)).asUTC)),
    types = Seq(MailboxTypeName))
  val firebasePushClient: FirebasePushClient = mock(classOf[FirebasePushClient])
}

trait FirebaseSubscriptionGetMethodContract {

  def bobUsername: Username = FirebaseSubscriptionGetMethodContract.currentContext.get().bobUsername

  def bobAccountId: String = FirebaseSubscriptionGetMethodContract.currentContext.get().bobAccountId

  def andreUsername: Username = FirebaseSubscriptionGetMethodContract.currentContext.get().andreUsername

  def firebaseSubscriptionCreateRequest: FirebaseSubscriptionCreationRequest =
    FirebaseSubscriptionGetMethodContract.currentContext.get().firebaseSubscriptionCreateRequest

  @BeforeEach
  def setUp(server: GuiceJamesServer, clock: UpdatableTickingClock): Unit = {
    clock.setInstant(Instant.now())

    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    val firebaseSubscriptionCreateRequest = FirebaseSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("ipad gen 9"),
      token = FirebaseToken(s"fire-base-token-$uniqueSuffix"),
      expires = Some(FirebaseSubscriptionExpiredTime(UTCDate(ZonedDateTime.now().plusDays(1)).asUTC)),
      types = Seq(MailboxTypeName))
    FirebaseSubscriptionGetMethodContract.currentContext.set(FirebaseSubscriptionGetMethodContract.TestContext(
      bobUsername = bob,
      bobAccountId = Hashing.sha256().hashString(bob.asString(), StandardCharsets.UTF_8).toString,
      andreUsername = andre,
      firebaseSubscriptionCreateRequest = firebaseSubscriptionCreateRequest))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(bob.asString(), BOB_PASSWORD)
      .addUser(andre.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
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
           |      "accountId": "$bobAccountId",
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
      .createSubscription(bobUsername, firebaseSubscriptionCreateRequest)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
         |                        "expires": "${firebaseSubscriptionCreateRequest.expires.get.value.format(TIME_FORMATTER)}",
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
  def getShouldNotReturnExpiredSubscriptionAndTriggerTheDeletion(server: GuiceJamesServer, clock: UpdatableTickingClock): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(bobUsername, firebaseSubscriptionCreateRequest)

    clock.setInstant(ZonedDateTime.now().plusDays(100).toInstant)

    assertThatJson(`given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
      .asString)
      .isEqualTo(
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

    assertThat(server.getProbe(classOf[FirebaseSubscriptionProbe])
      .retrieveSubscription(bobUsername, firebaseSubscription.id))
      .isNull()
  }

  @Test
  def getShouldReturnEntriesWhenHaveSeveralSubscription(server: GuiceJamesServer): Unit = {
    val createRequest2: FirebaseSubscriptionCreationRequest = firebaseSubscriptionCreateRequest.copy(
      deviceClientId = DeviceClientId("ipad gen 10"),
      token = FirebaseToken(s"fire-base-token-${UUID.randomUUID()}"))

    val firebaseSubscription1 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(bobUsername, firebaseSubscriptionCreateRequest)
    val firebaseSubscription2 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(bobUsername, createRequest2)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
      .withOptions(IGNORING_ARRAY_ORDER)
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
           |                        "expires": "${firebaseSubscriptionCreateRequest.expires.get.value.format(TIME_FORMATTER)}",
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
      .createSubscription(bobUsername, firebaseSubscriptionCreateRequest)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
      .createSubscription(bobUsername, firebaseSubscriptionCreateRequest)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
      .createSubscription(bobUsername, firebaseSubscriptionCreateRequest)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
         |                        "expires": "${firebaseSubscriptionCreateRequest.expires.get.value.format(TIME_FORMATTER)}",
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
      .createSubscription(andreUsername, firebaseSubscriptionCreateRequest)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
      .createSubscription(andreUsername, firebaseSubscriptionCreateRequest)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
      .createSubscription(bobUsername, firebaseSubscriptionCreateRequest)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
      .createSubscription(bobUsername, firebaseSubscriptionCreateRequest)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
      .createSubscription(bobUsername, firebaseSubscriptionCreateRequest)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |    {
           |      "accountId": "$bobAccountId",
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
           |      "accountId": "$bobAccountId",
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
           |      "accountId": "$bobAccountId",
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
