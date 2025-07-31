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

import java.util.concurrent.TimeUnit

import com.linagora.tmail.james.common.LinagoraForwardSetMethodContract.{CALMLY_AWAIT, CEDRIC_PASSWORD}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.core.builder.MimeMessageBuilder
import org.apache.james.jmap.MessageIdProbe
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.model.{MailboxPath, MessageResult, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.rrt.lib.{Mapping, MappingSource}
import org.apache.james.utils.{DataProbeImpl, SMTPMessageSender}
import org.apache.mailet.base.test.FakeMail
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import scala.jdk.CollectionConverters._

object LinagoraForwardSetMethodContract {
  private lazy val CALMLY_AWAIT: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
  private lazy val CEDRIC_PASSWORD: String = "cedricpassword"
}

trait LinagoraForwardSetMethodContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
      .addUser(CEDRIC.asString(), CEDRIC_PASSWORD)

    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(BOB))
    mailboxProbe.createMailbox(MailboxPath.inbox(ANDRE))
    mailboxProbe.createMailbox(MailboxPath.inbox(CEDRIC))

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def forwardSetShouldFailWhenWrongAccountId(): Unit = {
    val request: String =
      """{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "unknownAccountId",
        |        "update": {
        |            "singleton": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "targetA@domain.org",
        |                    "targetB@domain.org"
        |                ]
        |            }
        |        }
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["error", {
         |      "type": "accountNotFound"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def forwardSetShouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val request: String =
      """{
        |    "using": [ "urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "update": {
        |            "singleton": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "targetA@domain.org",
        |                    "targetB@domain.org"
        |                ]
        |            }
        |        }
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |        [
         |            "error",
         |            {
         |                "type": "unknownMethod",
         |                "description": "Missing capability(ies): com:linagora:params:jmap:forward"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def forwardSetShouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val request: String =
      """{
        |    "using": [],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "update": {
        |            "singleton": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "targetA@domain.org",
        |                    "targetB@domain.org"
        |                ]
        |            }
        |        }
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:jmap:forward"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateShouldReturnSuccess(): Unit = {
    val request: String =
      """{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "update": {
        |            "singleton": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "targetA@domain.org",
        |                    "targetB@domain.org"
        |                ]
        |            }
        |        }
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "updated": {"singleton":{}}
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @ParameterizedTest
  @ValueSource(booleans = Array(true, false))
  def updateShouldModifiedForwardEntry(localCopy: Boolean): Unit = {
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "update": {
         |            "singleton": {
         |                "localCopy": $localCopy,
         |                "forwards": [
         |                    "${ANDRE.asMailAddress().asString()}"
         |                ]
         |            }
         |        }
         |      }, "c1"],
         |      ["Forward/get", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "ids": ["singleton"]
         |      }, "c2" ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "updated": {"singleton":{}}
         |    }, "c1"],
         |    ["Forward/get", {
         |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |       "notFound": [],
         |       "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
         |       "list": [
         |         { "id": "singleton",
         |            "localCopy": $localCopy,
         |            "forwards": [ "${ANDRE.asMailAddress().asString()}"]
         |         }
         |       ]
         |    }, "c2" ]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateForwardLoopShouldFail(guiceJamesServer: GuiceJamesServer): Unit = {
    // GIVEN Andre forwards mails to Bob
    guiceJamesServer.getProbe(classOf[DataProbeImpl])
      .addMapping(MappingSource.fromUser(ANDRE), Mapping.forward(BOB.asString()))

    // WHEN Bob Forward/set to forward mails to Andre
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "update": {
         |            "singleton": {
         |                "localCopy": false,
         |                "forwards": [
         |                    "${ANDRE.asMailAddress().asString()}"
         |                ]
         |            }
         |        }
         |      }, "c1"],
         |      ["Forward/get", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "ids": ["singleton"]
         |      }, "c2" ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    // THEN Forward/set should reject the loop request
    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Forward/set",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "newState": "${INSTANCE.value}",
         |                "notUpdated": {
         |                    "singleton": {
         |                        "type": "invalidPatch",
         |                        "description": "Creation of redirection of ${BOB.asString()} to forward:${ANDRE.asString()} would lead to a loop, operation not performed"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ],
         |        [
         |            "Forward/get",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "notFound": [],
         |                "state": "${INSTANCE.value}",
         |                "list": [
         |                    {
         |                        "id": "singleton",
         |                        "localCopy": true,
         |                        "forwards": []
         |                    }
         |                ]
         |            },
         |            "c2"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldReturnSuccessWhenForwardsIsEmpty(): Unit = {
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "update": {
         |            "singleton": {
         |                "localCopy": true,
         |                "forwards": []
         |            }
         |        }
         |      }, "c1"],
         |      ["Forward/get", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "ids": ["singleton"]
         |      }, "c2" ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "updated": {"singleton":{}}
         |    }, "c1"],
         |    ["Forward/get", {
         |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |       "notFound": [],
         |       "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
         |       "list": [
         |         { "id": "singleton",
         |            "localCopy": true,
         |            "forwards": []
         |         }
         |       ]
         |    }, "c2" ]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenMissingForwards(): Unit = {
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "update": {
         |            "singleton": {
         |                "localCopy": true
         |            }
         |        }
         |      }, "c1"]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "notUpdated": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "Missing '/forwards' property"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenMissingLocalCopy(): Unit = {
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "update": {
         |            "singleton": {
         |               "forwards": []
         |            }
         |        }
         |      }, "c1"]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "notUpdated": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "Missing '/localCopy' property"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenInvalidKey(): Unit = {
    val request: String =
      """{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "update": {
        |            "invalidKey": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "targetA@domain.org",
        |                    "targetB@domain.org"
        |                ]
        |            }
        |        }
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "notUpdated": {
         |        "invalidKey": {
         |          "type": "invalidArguments",
         |          "description": "id invalidKey must be singleton"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenInvalidLocalCopy(): Unit = {
    val request: String =
      """{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "update": {
        |            "singleton": {
        |                "localCopy": "invalid",
        |                "forwards": [
        |                    "targetA@domain.org",
        |                    "targetB@domain.org"
        |                ]
        |            }
        |        }
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "notUpdated": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "'/localCopy' property is not valid: error.expected.jsboolean"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenInvalidForwards(): Unit = {
    val request: String =
      """{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "update": {
        |            "singleton": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "123$#%$#invalid"
        |                ]
        |            }
        |        }
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "notUpdated": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "'/forwards(0)' property is not valid: Invalid mailAddress: Out of data at position 1 in '123$$#%$$#invalid'"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldNoopWhenEmptyMap(): Unit = {
    val request: String =
      """{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "update": {}
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenMultiplePatchObjects(): Unit = {
    val request: String =
      """{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "update": {
        |            "singleton": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "targetA@domain.org",
        |                    "targetB@domain.org"
        |                ]
        |            },
        |            "singleton2": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "targetA@domain.org",
        |                    "targetB@domain.org"
        |                ]
        |            }
        |        }
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "updated": {"singleton": {} },
         |      "notUpdated": {
         |        "singleton2": {
         |          "type": "invalidArguments",
         |          "description": "id singleton2 must be singleton"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def createShouldFail(): Unit = {
    val request: String =
      """{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "create": {
        |            "singleton": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "targetA@domain.org",
        |                    "targetB@domain.org"
        |                ]
        |            }
        |        }
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "notCreated": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "'create' is not supported on singleton objects"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def destroyShouldFail(): Unit = {
    val request: String =
      """{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "destroy": ["singleton"]
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
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
         |  "methodResponses": [
         |    ["Forward/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "newState": "${INSTANCE.value}",
         |      "notDestroyed": {
         |        "singleton": {
         |          "type": "invalidArguments",
         |          "description": "'destroy' is not supported on singleton objects"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def messageShouldBeForwardedToDestinationForwards(server: GuiceJamesServer): Unit = {
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "update": {
         |            "singleton": {
         |                "localCopy": true,
         |                "forwards": [ "${ANDRE.asMailAddress().asString()}"]
         |            }
         |        }
         |      }, "c1"]
         |    ]
         |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)

    val mail: FakeMail = FakeMail.builder()
      .name("mail1")
      .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
        .setSender(BOB.asString())
        .addToRecipient(BOB.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(BOB.asString())
      .recipient(BOB.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(BOB.asString(), BOB_PASSWORD)
      .sendMessage(mail)

    CALMLY_AWAIT.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      assertThat(listAllMessageResult(server, ANDRE)).hasSize(1)
    }
  }

  @Test
  def messageShouldBeForwardedToOwnerWhenLocalCopyIsTrue(server: GuiceJamesServer): Unit = {
    assertThat(listAllMessageResult(server, BOB)).hasSize(0)
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "update": {
         |            "singleton": {
         |                "localCopy": true,
         |                "forwards": []
         |            }
         |        }
         |      }, "c1"]
         |    ]
         |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)

    val mail: FakeMail = FakeMail.builder()
      .name("mail1")
      .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
        .setSender(ANDRE.asString())
        .addToRecipient(BOB.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(ANDRE.asString())
      .recipient(BOB.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(ANDRE.asString(), ANDRE_PASSWORD)
      .sendMessage(mail)

    CALMLY_AWAIT.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      assertThat(listAllMessageResult(server, BOB)).hasSize(1)
    }
  }

  @Test
  def messageShouldNOTBeForwardedToOwnerWhenLocalCopyIsFalse(server: GuiceJamesServer): Unit = {
    assertThat(listAllMessageResult(server, BOB)).hasSize(0)
    assertThat(listAllMessageResult(server, ANDRE)).hasSize(0)
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "update": {
         |            "singleton": {
         |                "localCopy": false,
         |                "forwards": [ "${ANDRE.asMailAddress().asString()}"]
         |            }
         |        }
         |      }, "c1"]
         |    ]
         |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)

    val mail: FakeMail = FakeMail.builder()
      .name("mail1")
      .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
        .setSender(CEDRIC.asString())
        .addToRecipient(BOB.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(CEDRIC.asString())
      .recipient(BOB.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(CEDRIC.asString(), CEDRIC_PASSWORD)
      .sendMessage(mail)

    CALMLY_AWAIT.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      assertThat(listAllMessageResult(server, ANDRE)).hasSize(1)
      assertThat(listAllMessageResult(server, BOB)).hasSize(0)
    }
  }

  @Test
  def messageShouldNOTBeForwardedToOtherNotInDestinationForwards(server: GuiceJamesServer): Unit = {
    assertThat(listAllMessageResult(server, BOB)).hasSize(0)
    assertThat(listAllMessageResult(server, ANDRE)).hasSize(0)
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "update": {
         |            "singleton": {
         |                "localCopy": true,
         |                "forwards": []
         |            }
         |        }
         |      }, "c1"]
         |    ]
         |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)

    val mail: FakeMail = FakeMail.builder()
      .name("mail1")
      .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
        .setSender(CEDRIC.asString())
        .addToRecipient(BOB.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(CEDRIC.asString())
      .recipient(BOB.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(CEDRIC.asString(), CEDRIC_PASSWORD)
      .sendMessage(mail)

    CALMLY_AWAIT.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      assertThat(listAllMessageResult(server, ANDRE)).hasSize(0)
      assertThat(listAllMessageResult(server, BOB)).hasSize(1)
    }
  }

  @Test
  def messageShouldBeForwardedToDestinationForwardsAndOwner(server: GuiceJamesServer): Unit = {
    assertThat(listAllMessageResult(server, ANDRE)).hasSize(0)
    assertThat(listAllMessageResult(server, BOB)).hasSize(0)

    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |        "update": {
         |            "singleton": {
         |                "localCopy": true,
         |                "forwards": [ "${ANDRE.asMailAddress().asString()}"]
         |            }
         |        }
         |      }, "c1"]
         |    ]
         |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)

    val mail: FakeMail = FakeMail.builder()
      .name("mail1")
      .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
        .setSender(CEDRIC.asString())
        .addToRecipient(BOB.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(CEDRIC.asString())
      .recipient(BOB.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(CEDRIC.asString(), CEDRIC_PASSWORD)
      .sendMessage(mail)

    CALMLY_AWAIT.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      assertThat(listAllMessageResult(server, ANDRE)).hasSize(1)
      assertThat(listAllMessageResult(server, BOB)).hasSize(1)
    }
  }
  @Test
  def setShouldRejectFromDelegatedAccount(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    val bobAccountId = ACCOUNT_ID

    val request: String =
      s"""{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "$bobAccountId",
        |        "update": {
        |            "singleton": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "targetA@domain.org",
        |                    "targetB@domain.org"
        |                ]
        |            }
        |        }
        |      }, "c1"]
        |    ]
        |  }""".stripMargin

    val response = `given`(baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build)
      .body(request)
  .when()
      .post()
  .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"error",
           |	{
           |		"type": "forbidden",
           |		"description": "Access to other accounts settings is forbidden"
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  private def listAllMessageResult(guiceJamesServer: GuiceJamesServer, username: Username): java.util.List[MessageResult] =
    guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
      .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build, username.asString(), 100)
      .asScala
      .flatMap(messageId => guiceJamesServer.getProbe(classOf[MessageIdProbe]).getMessages(messageId, username).asScala.headOption)
      .toList.asJava

}