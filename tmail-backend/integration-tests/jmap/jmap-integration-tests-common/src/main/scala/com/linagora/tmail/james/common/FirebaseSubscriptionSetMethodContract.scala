package com.linagora.tmail.james.common

import com.linagora.tmail.james.common.FirebaseSubscriptionGetMethodContract.{FIREBASE_SUBSCRIPTION_CREATE_REQUEST, TIME_FORMATTER}
import com.linagora.tmail.james.common.FirebaseSubscriptionSetMethodContract.firebasePushClient
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient
import com.linagora.tmail.james.jmap.model.{DeviceClientId, FirebaseSubscriptionCreationRequest, FirebaseToken}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.path.json.JsonPath
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.change.ThreadTypeName
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import reactor.core.publisher.Mono

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object FirebaseSubscriptionSetMethodContract {
  val firebasePushClient: FirebasePushClient = mock(classOf[FirebasePushClient])
}

trait FirebaseSubscriptionSetMethodContract {

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

    when(firebasePushClient.validateToken(any()))
      .thenReturn(Mono.just(true))
  }

  @Test
  def setShouldReturnCreatedWhenValidCreationRequest(): Unit = {
    val response = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "created": {
         |                    "4f29": {
         |                        "id": "$${json-unit.ignore}",
         |                        "expires": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def validCreationRequestShouldStorageSubscription(): Unit = {
    val jsonPath: JsonPath = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .jsonPath()

    val subscriptionId: String= jsonPath.get("methodResponses[0][1].created.4f29.id")
    val expiredTime:String = jsonPath.get("methodResponses[0][1].created.4f29.expires")

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
         |                        "expires": "$expiredTime",
         |                        "id": "$subscriptionId",
         |                        "deviceClientId": "a889-ffea-910",
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
  def invalidFcmTokenShouldNotStoreSubscription(): Unit = {
    when(firebasePushClient.validateToken(any()))
      .thenReturn(Mono.just(false))

    val response: String = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "invalidToken1",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notCreated": {
         |                    "4f29": {
         |                        "type": "invalidArguments",
         |                        "description": "Token is not valid",
         |                        "properties": [
         |                            "token"
         |                        ]
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationRequestShouldCreatedWhenUnExpectedException(): Unit = {
    when(firebasePushClient.validateToken(any()))
      .thenReturn(Mono.error(new RuntimeException))

    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |          "create": {
           |            "4f29": {
           |              "deviceClientId": "a889-ffea-910",
           |              "token": "token1",
           |              "types": ["Mailbox"]
           |            }
           |          }
           |        },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "created": {
         |                    "4f29": {
         |                        "id": "$${json-unit.ignore}",
         |                        "expires": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationRequestShouldFailWhenMissTokenProperty(): Unit = {
    val response = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notCreated": {
         |                    "4f29": {
         |                        "type": "invalidArguments",
         |                        "description": "Missing '/token' property"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationRequestShouldFailWhenMissDeviceClientIdProperty(): Unit = {
    val response = `given`
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
           |                  "token": "token1",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notCreated": {
         |                    "4f29": {
         |                        "type": "invalidArguments",
         |                        "description": "Missing '/deviceClientId' property"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationRequestShouldFailWhenMissTypesProperty(): Unit = {
    val response = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1"
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notCreated": {
         |                    "4f29": {
         |                        "type": "invalidArguments",
         |                        "description": "Missing '/types' property"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationRequestShouldFailWhenInvalidTypes(): Unit = {
    val response = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "types": ["invalidType1"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notCreated": {
         |                    "4f29": {
         |                        "type": "invalidArguments",
         |                        "description": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationRequestShouldFailWhenEmptyTypes(): Unit = {
    val response = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "types": []
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notCreated": {
         |                    "4f29": {
         |                        "type": "invalidArguments",
         |                        "description": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationRequestShouldSupportExpiresProperty(): Unit = {
    val expires: String = UTCDate(ZonedDateTime.now().plusDays(1)).asUTC.format(TIME_FORMATTER)

    val response = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "expires": "$expires",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "created": {
         |                    "4f29": {
         |                        "id": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationResponseShouldReturnExpiresPropertyWhenExpiresGreaterThanThreshold(): Unit = {
    val expires: String = UTCDate(ZonedDateTime.now().plusDays(100)).asUTC.format(TIME_FORMATTER)

    val response = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "expires": "$expires",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "created": {
         |                    "4f29": {
         |                        "id": "$${json-unit.ignore}",
         |                        "expires" : "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationRequestShouldFailWhenInvalidExpiresProperty(): Unit = {
    val expires: String = UTCDate(ZonedDateTime.now().minusDays(1)).asUTC.format(TIME_FORMATTER)

    val response = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "expires": "$expires",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notCreated": {
         |                    "4f29": {
         |                        "type": "invalidArguments",
         |                        "description": "`$expires` expires must be greater than now",
         |                        "properties": [
         |                            "expires"
         |                        ]
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def creationRequestShouldFailWhenExpiresIsNotFormatted(): Unit = {
    val response = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "expires": "notFormat",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notCreated": {
         |                    "4f29": {
         |                        "type": "invalidArguments",
         |                        "description": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def setShouldSuccessWhenSeveralCreationRequest(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "create": {
           |                "k1": {
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "types": ["Mailbox"]
           |                },
           |                "k2": {
           |                  "deviceClientId": "a889-ffea-912",
           |                  "token": "token2",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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

    assertThatJson(response)
      .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "created": {
         |                    "k1": {
         |                        "id": "$${json-unit.ignore}",
         |                        "expires": "$${json-unit.ignore}"
         |                    },
         |                    "k2": {
         |                        "id": "$${json-unit.ignore}",
         |                        "expires": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def setShouldReturnResponseWhenMixCases(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "create": {
           |                "k1": {
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "types": ["Mailbox"]
           |                },
           |                "k2": {
           |                  "deviceClientId": "a889-ffea-912"
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "created": {
         |                    "k1": {
         |                        "id": "$${json-unit.ignore}",
         |                        "expires": "$${json-unit.ignore}"
         |                    }
         |                },
         |                "notCreated": {
         |                    "k2": {
         |                        "type": "invalidArguments",
         |                        "description": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def setShouldSupportBackReference(): Unit = {
    val response = `given`
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "com:linagora:params:jmap:firebase:push"
           |    ],
           |    "methodCalls": [
           |        [
           |            "FirebaseRegistration/set",
           |            {
           |                "create": {
           |                    "4f29": {
           |                        "deviceClientId": "a889-ffea-910",
           |                        "token": "token1",
           |                        "types": [
           |                            "Mailbox"
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "FirebaseRegistration/get",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "ids": [
           |                    "#4f29"
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "created": {
         |                    "4f29": {
         |                        "id": "$${json-unit.ignore}",
         |                        "expires": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ],
         |        [
         |            "FirebaseRegistration/get",
         |            {
         |                "notFound": [],
         |                "list": [
         |                    {
         |                        "id": "$${json-unit.ignore}",
         |                        "deviceClientId": "a889-ffea-910",
         |                        "expires": "$${json-unit.ignore}",
         |                        "types": [
         |                            "Mailbox"
         |                        ]
         |                    }
         |                ]
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def setShouldFailWhenOmittingOneCapability(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core"],
           |  "methodCalls": [[
           |    "FirebaseRegistration/set",
           |        {
           |            "create": {
           |                "4f29": {
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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
  def setShouldFailWhenOmittingAllCapability(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [],
           |  "methodCalls": [[
           |    "FirebaseRegistration/get",
           |        {
           |            "create": {
           |                "4f29": {
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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

  @Test
  def creationShouldFailWhenTokenIsNotUnique(server: GuiceJamesServer): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val response = `given`
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
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "${FIREBASE_SUBSCRIPTION_CREATE_REQUEST.token.value}",
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notCreated": {
         |                    "4f29": {
         |                        "type": "invalidArguments",
         |                        "description": "deviceToken must be unique",
         |                        "properties": [
         |                            "token"
         |                        ]
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateTypesShouldReturnUpdatedResponseWhenValidUpdateRequest(server: GuiceJamesServer): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription.id.serialize}": {
           |                  "types": ["Mailbox", "Email"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "updated": {
         |                   "${firebaseSubscription.id.serialize}": {}
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateTypesShouldFailWhenTypesPropertyIsEmpty(server: GuiceJamesServer): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription.id.serialize}": {
           |                  "types": []
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notUpdated": [
         |                    [
         |                        "${firebaseSubscription.id.serialize}",
         |                        {
         |                            "type": "invalidArguments",
         |                            "description": "Must not empty",
         |                            "properties": [
         |                                "types"
         |                            ]
         |                        }
         |                    ]
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateTypeRequestShouldChangeSubscriptionData(server: GuiceJamesServer): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

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
           |                "${firebaseSubscription.id.serialize}": {
           |                  "types": ["Mailbox", "Email"]
           |                }
           |              }
           |        },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)

    val response = `given`
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "com:linagora:params:jmap:firebase:push"
           |    ],
           |    "methodCalls": [
           |        [
           |            "FirebaseRegistration/get",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "ids": null
           |            },
           |            "c1"
           |        ]
           |    ]
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

    assertThatJson(response)
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         | [
         |            "FirebaseRegistration/get",
         |            {
         |                "notFound": [
         |
         |                ],
         |                "list": [
         |                    {
         |                        "id": "${firebaseSubscription.id.serialize}",
         |                        "deviceClientId": "ipad gen 9",
         |                        "expires": "$${json-unit.ignore}",
         |                        "types": [
         |                            "Mailbox",
         |                            "Email"
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
  def updateShouldFailWhenInvalidTypes(server: GuiceJamesServer): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription.id.serialize}": {
           |                  "types": ["InvalidType1", "Email"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notUpdated": [
         |                    [
         |                        "${firebaseSubscription.id.serialize}",
         |                        {
         |                            "type": "invalidArguments",
         |                            "description": "Unknown typeName InvalidType1",
         |                            "properties": [
         |                                "types"
         |                            ]
         |                        }
         |                    ]
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenInvalidProperty(server: GuiceJamesServer): Unit = {
    val firebaseSubscription = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription.id.serialize}": {
           |                  "property1": "propertyValue"
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notUpdated": [
         |                    [
         |                        "${firebaseSubscription.id.serialize}",
         |                        {
         |                            "type": "invalidArguments",
         |                            "description": "Some unknown properties were specified",
         |                            "properties": [
         |                                "property1"
         |                            ]
         |                        }
         |                    ]
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenNotFoundId(): Unit = {
    val notFoundId = UUID.randomUUID().toString

    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "$notFoundId": {
           |                  "types": ["Email"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notUpdated": [
         |                    [
         |                        "$notFoundId",
         |                        {
         |                            "type": "notFound",
         |                            "description": null
         |                        }
         |                    ]
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenCanNotParseId(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "123": {
           |                  "types": ["Email"]
           |                }
           |              }
           |        },
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
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notUpdated": [
         |                    [
         |                        "123",
         |                        {
         |                            "type": "invalidArguments",
         |                            "description": "FirebaseSubscriptionId is invalid"
         |                        }
         |                    ]
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def setShouldSuccessWhenUpdateSeveralSubscriptions(server: GuiceJamesServer): Unit = {
    val firebaseSubscription1 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val firebaseSubscription2 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FirebaseSubscriptionCreationRequest(deviceClientId = DeviceClientId("device2"),
        token = FirebaseToken("token2"),
        types = Seq(ThreadTypeName)))

    val newExpires = UTCDate(ZonedDateTime.now().plusDays(3)).asUTC.format(TIME_FORMATTER)

    val updateResponse = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription1.id.serialize}": {
           |                  "types": ["Email"],
           |                  "expires": "$newExpires"
           |                },
           |                "${firebaseSubscription2.id.serialize}": {
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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

    assertThatJson(updateResponse)
      .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "updated": {
         |                    "${firebaseSubscription1.id.serialize}": {
         |                    },
         |                    "${firebaseSubscription2.id.serialize}": {
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)


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
         |                "notFound": [
         |                ],
         |                "list": [
         |                    {
         |                        "id": "${firebaseSubscription1.id.serialize}",
         |                        "deviceClientId": "ipad gen 9",
         |                        "expires": "$${json-unit.ignore}",
         |                        "types": [
         |                            "Email"
         |                        ]
         |                    },
         |                    {
         |                        "id": "${firebaseSubscription2.id.serialize}",
         |                        "deviceClientId": "device2",
         |                        "expires": "$${json-unit.ignore}",
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
  def updateRequestShouldReturnCorrectResponseWhenMixCases(server: GuiceJamesServer): Unit = {
    val firebaseSubscription1 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val updateResponse = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription1.id.serialize}": {
           |                  "types": ["Email"]
           |                },
           |                "notFound": {
           |                  "types": ["Mailbox"]
           |                }
           |              }
           |        },
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

    assertThatJson(updateResponse).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "updated": {
         |                    "${firebaseSubscription1.id.serialize}": {
         |                    }
         |                },
         |                "notUpdated": [
         |                    [
         |                        "notFound",
         |                        {
         |                            "type": "invalidArguments",
         |                            "description": "FirebaseSubscriptionId is invalid"
         |                        }
         |                    ]
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def destroyShouldSucceed(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[FirebaseSubscriptionProbe])
    val firebaseSubscription = probe
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core",
         |              "com:linagora:params:jmap:firebase:push"],
         |    "methodCalls": [
         |      [
         |        "FirebaseRegistration/set",
         |        {
         |            "destroy": ["${firebaseSubscription.id.value.toString}"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "FirebaseRegistration/set",
           |            {
           |                "destroyed": ["${firebaseSubscription.id.value.toString}"]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrieveSubscription(BOB, firebaseSubscription.id)).isNull()
  }

  @Test
  def destroyShouldFailWhenInvalidId(): Unit = {
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core",
         |              "com:linagora:params:jmap:firebase:push"],
         |    "methodCalls": [
         |      [
         |        "FirebaseRegistration/set",
         |        {
         |            "destroy": ["invalid"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "FirebaseRegistration/set",
           |            {
           |              "notDestroyed": [[
           |                "invalid",
           |                {
           |                    "type": "invalidArguments",
           |                    "description": "invalid is not a FirebaseSubscriptionId: FirebaseSubscriptionId is invalid"
           |                }
           |              ]]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldNotFailWhenUnknownId(): Unit = {
    val id = UUID.randomUUID().toString

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core",
         |              "com:linagora:params:jmap:firebase:push"],
         |    "methodCalls": [
         |      [
         |        "FirebaseRegistration/set",
         |        {
         |            "destroy": ["$id"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "FirebaseRegistration/set",
           |            {
           |                "destroyed":["$id"]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldHandleMixedCases(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[FirebaseSubscriptionProbe])
    val createRequest2: FirebaseSubscriptionCreationRequest = FIREBASE_SUBSCRIPTION_CREATE_REQUEST.copy(
      deviceClientId = DeviceClientId("ipad gen 10"),
      token = FirebaseToken("fire-base-token-3"))

    val firebaseSubscription1 = probe
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)
    val firebaseSubscription2 = probe
      .createSubscription(BOB, createRequest2)

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core",
         |              "com:linagora:params:jmap:firebase:push"],
         |    "methodCalls": [
         |      [
         |        "FirebaseRegistration/set",
         |        {
         |            "destroy": ["${firebaseSubscription1.id.value.toString}", "${firebaseSubscription2.id.value.toString}", "invalid"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .when(net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "FirebaseRegistration/set",
           |            {
           |                "destroyed": [
           |                    "${firebaseSubscription1.id.value.toString}",
           |                    "${firebaseSubscription2.id.value.toString}"
           |                ],
           |                "notDestroyed": [[
           |                    "invalid",
           |                    {
           |                        "type": "invalidArguments",
           |                        "description": "invalid is not a FirebaseSubscriptionId: FirebaseSubscriptionId is invalid"
           |                    }
           |                ]]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldNotRevokeIdOfOtherAccount(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[FirebaseSubscriptionProbe])
    val firebaseSubscription = probe
      .createSubscription(ANDRE, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core",
         |              "com:linagora:params:jmap:firebase:push"],
         |    "methodCalls": [
         |      [
         |        "FirebaseRegistration/set",
         |        {
         |            "destroy": ["${firebaseSubscription.id.value.toString}"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "FirebaseRegistration/set",
           |            {
           |                "destroyed": ["${firebaseSubscription.id.value.toString}"]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrieveSubscription(ANDRE, firebaseSubscription.id)).isNotNull()
  }

  @Test
  def updateExpiresShouldReturnUpdatedWhenValidRequest(server: GuiceJamesServer): Unit = {
    val firebaseSubscription1 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val newExpires = UTCDate(ZonedDateTime.now().plusDays(3)).asUTC.format(TIME_FORMATTER)

    val updateResponse = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription1.id.serialize}": {
           |                  "expires": "$newExpires"
           |                }
           |              }
           |        },
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

    assertThatJson(updateResponse).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "updated": {
         |                    "${firebaseSubscription1.id.serialize}": {}
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateExpiresShouldReturnNotUpdatedWhenInvalidExpires(server: GuiceJamesServer): Unit = {
    val firebaseSubscription1 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val updateResponse = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription1.id.serialize}": {
           |                  "expires": "invalid"
           |                }
           |              }
           |        },
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

    assertThatJson(updateResponse).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notUpdated": [
         |                    [
         |                        "${firebaseSubscription1.id.serialize}",
         |                        {
         |                            "type": "invalidArguments",
         |                            "description": "This string can not be parsed to UTCDate",
         |                            "properties": [
         |                                "expires"
         |                            ]
         |                        }
         |                    ]
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateExpiresShouldReturnNotUpdatedWhenExpiresIsNotCorrectFormat(server: GuiceJamesServer): Unit = {
    val firebaseSubscription1 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val newExpires = UTCDate(ZonedDateTime.now().plusDays(3)).asUTC.format(DateTimeFormatter.ofPattern("yyyy-dd-MM'T'HH:mm"))

    val updateResponse = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription1.id.serialize}": {
           |                  "expires": "$newExpires"
           |                }
           |              }
           |        },
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

    assertThatJson(updateResponse).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notUpdated": [
         |                    [
         |                        "${firebaseSubscription1.id.serialize}",
         |                        {
         |                            "type": "invalidArguments",
         |                            "description": "This string can not be parsed to UTCDate",
         |                            "properties": [
         |                                "expires"
         |                            ]
         |                        }
         |                    ]
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }


  @Test
  def updateExpiresShouldReturnNotUpdatedWhenExpiresIsOld(server: GuiceJamesServer): Unit = {
    val firebaseSubscription1 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val newExpires = UTCDate(ZonedDateTime.now().minusDays(3)).asUTC.format(TIME_FORMATTER)

    val updateResponse = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription1.id.serialize}": {
           |                  "expires": "$newExpires"
           |                }
           |              }
           |        },
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

    assertThatJson(updateResponse).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "notUpdated": [
         |                    [
         |                        "${firebaseSubscription1.id.serialize}",
         |                        {
         |                            "type": "invalidArguments",
         |                            "description": "`$newExpires` expires must be greater than now",
         |                            "properties": [
         |                                "expires"
         |                            ]
         |                        }
         |                    ]
         |                ]
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateExpiresShouldSuccessWhenExpiresIsGreaterThanThreshold(server: GuiceJamesServer): Unit = {
    val firebaseSubscription1 = server.getProbe(classOf[FirebaseSubscriptionProbe])
      .createSubscription(BOB, FIREBASE_SUBSCRIPTION_CREATE_REQUEST)

    val newExpires = UTCDate(ZonedDateTime.now().plusMonths(1)).asUTC.format(TIME_FORMATTER)

    val updateResponse = `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "update": {
           |                "${firebaseSubscription1.id.serialize}": {
           |                  "expires": "$newExpires"
           |                }
           |              }
           |        },
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

    assertThatJson(updateResponse).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "FirebaseRegistration/set",
         |            {
         |                "updated": {
         |                    "${firebaseSubscription1.id.serialize}": {
         |                        "expires": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }
}
