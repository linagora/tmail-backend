package com.linagora.tmail.james.common

import com.linagora.tmail.james.common.FirebaseSubscriptionGetMethodContract.TIME_FORMATTER
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.path.json.JsonPath
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

import java.time.ZonedDateTime


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
  }

  @Test
  def setShouldReturnCreatedWhenInvalidCreationRequest(): Unit = {
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
         |                        "expires": "${expiredTime}",
         |                        "id": "${subscriptionId}",
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
         |                        "description": "Missing '/token' property in FirebaseSubscription object"
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
         |                        "description": "Missing '/deviceClientId' property in FirebaseSubscription object"
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
         |                        "description": "Missing '/types' property in FirebaseSubscription object"
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
           |                  "expires": "${expires}",
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
         |                        "expires":  "${expires}"
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
           |                  "expires": "${expires}",
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
         |                        "description": "`${expires}` expires must be greater than now",
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
}
