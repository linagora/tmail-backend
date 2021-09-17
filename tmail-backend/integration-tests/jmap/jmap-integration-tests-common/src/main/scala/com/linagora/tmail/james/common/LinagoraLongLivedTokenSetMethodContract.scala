package com.linagora.tmail.james.common

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.{JsString, Json}

trait LinagoraLongLivedTokenSetMethodContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
  }

  @Test
  def methodShouldReturnFailWhenMissingOneCapability(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "My android device"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState":"${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): com:linagora:params:long:lived:token"
         |    },"c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldReturnFailWhenMissingAllCapabilities(): Unit = {
    val request: String =
      s"""{
         |  "using": [],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "My android device"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState":"${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:long:lived:token"
         |    },"c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldFailWhenWrongAccountId(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "unknownAccountId",
         |      "create": {
         |        "K38": {
         |          "deviceId": "My android device"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |            "error",
         |            {
         |                "type": "accountNotFound"
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def methodShouldFailWhenDeviceIdTooLength(): Unit = {
    val deviceId: String = "a".repeat(501)
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "$deviceId"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "LongLivedToken/set",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "notCreated": {
         |                    "K38": {
         |                        "type": "invalidArguments",
         |                        "description": "Length of deviceId must be smaller than 500"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldFailWhenDeviceIdIsEmpty(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": ""
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "LongLivedToken/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notCreated": {
         |                    "K38": {
         |                        "type": "invalidArguments",
         |                        "description": "deviceId must be not empty"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldReturnLongLivedToken(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "My android device"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "LongLivedToken/set",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "created": {
         |                    "K38": {
         |                        "id": "$${json-unit.ignore}",
         |                        "token": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldAcceptSeveralCreateRequest(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "My android device"
         |        },
         |        "K39": {
         |          "deviceId": "My laptop device"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "LongLivedToken/set",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "created": {
         |                    "K38": {
         |                        "id": "$${json-unit.ignore}",
         |                        "token": "$${json-unit.ignore}"
         |                    },
         |                    "K39": {
         |                        "id": "$${json-unit.ignore}",
         |                        "token": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldSuccessWhenMixedCase(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "My android device"
         |        },
         |        "K39": {
         |          "deviceId": "My laptop device"
         |        },
         |        "K40": {
         |          "deviceId": ""
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "LongLivedToken/set",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "created": {
         |                    "K38": {
         |                        "id": "$${json-unit.ignore}",
         |                        "token": "$${json-unit.ignore}"
         |                    },
         |                    "K39": {
         |                        "id": "$${json-unit.ignore}",
         |                        "token": "$${json-unit.ignore}"
         |                    }
         |                },
         |                "notCreated": {
         |                    "K40": {
         |                        "type": "invalidArguments",
         |                        "description": "deviceId must be not empty"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldFailWhenDoesNotHavePermissionToAccountId(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "My android device"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "error",
         |            {
         |                "type": "accountNotFound"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldSuccessWhenReCreateWithSameDeviceId(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "My android device"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    `given`
      .body(request)
    .when()
      .post()

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "LongLivedToken/set",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "created": {
         |                    "K38": {
         |                        "id": "$${json-unit.ignore}",
         |                        "token": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldSupportBackReferences(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "My android device"
         |        }
         |      }
         |    }, "c1"],
         |   ["Core/echo", {
         |      "arg1": "#K38"
         |    }, "c2"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "LongLivedToken/set",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "created": {
         |                    "K38": {
         |                        "id": "$${json-unit.ignore}",
         |                        "token": "$${json-unit.ignore}"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ],
         |        [
         |            "Core/echo",
         |            {
         |                "arg1": "$${json-unit.ignore}"
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)

    val longLivedTokenId:String = (((Json.parse(response) \\ "methodResponses" )
      .head \\ "created")
      .head \\ "id")
      .head.asInstanceOf[JsString].value

    val backReferenceValue:String = ((Json.parse(response) \\ "methodResponses" )
      .head \\ "arg1")
      .head.asInstanceOf[JsString].value

    assertThat(longLivedTokenId)
      .isEqualTo(backReferenceValue)
  }

}
