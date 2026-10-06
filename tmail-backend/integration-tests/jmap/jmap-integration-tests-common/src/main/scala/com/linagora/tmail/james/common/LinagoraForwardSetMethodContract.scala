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
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import com.google.common.hash.Hashing
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
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
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
  case class TestContext(bobUsername: Username, andreUsername: Username, cedricUsername: Username) {
    val bobAccountId: String = accountId(bobUsername)
  }

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()

  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  private lazy val CALMLY_AWAIT: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
  private lazy val CEDRIC_PASSWORD: String = "cedricpassword"
}

trait LinagoraForwardSetMethodContract {
  def bobUsername: Username = LinagoraForwardSetMethodContract.currentContext.get().bobUsername

  def bobAccountId: String = LinagoraForwardSetMethodContract.currentContext.get().bobAccountId

  def andreUsername: Username = LinagoraForwardSetMethodContract.currentContext.get().andreUsername

  def cedricUsername: Username = LinagoraForwardSetMethodContract.currentContext.get().cedricUsername

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    val cedric = Username.fromLocalPartWithDomain(s"cedric$uniqueSuffix", DOMAIN)
    LinagoraForwardSetMethodContract.currentContext.set(LinagoraForwardSetMethodContract.TestContext(bob, andre, cedric))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(bobUsername.asString(), BOB_PASSWORD)
      .addUser(andreUsername.asString(), ANDRE_PASSWORD)
      .addUser(cedricUsername.asString(), CEDRIC_PASSWORD)

    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(bobUsername))
    mailboxProbe.createMailbox(MailboxPath.inbox(andreUsername))
    mailboxProbe.createMailbox(MailboxPath.inbox(cedricUsername))

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
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
      s"""{
        |    "using": [ "urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "${bobAccountId}",
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
      s"""{
        |    "using": [],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "${bobAccountId}",
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
      s"""{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "${bobAccountId}",
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
         |      "accountId": "${bobAccountId}",
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
         |        "accountId": "${bobAccountId}",
         |        "update": {
         |            "singleton": {
         |                "localCopy": $localCopy,
         |                "forwards": [
         |                    "${andreUsername.asMailAddress().asString()}"
         |                ]
         |            }
         |        }
         |      }, "c1"],
         |      ["Forward/get", {
         |        "accountId": "${bobAccountId}",
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
         |      "accountId": "${bobAccountId}",
         |      "newState": "${INSTANCE.value}",
         |      "updated": {"singleton":{}}
         |    }, "c1"],
         |    ["Forward/get", {
         |       "accountId": "${bobAccountId}",
         |       "notFound": [],
         |       "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
         |       "list": [
         |         { "id": "singleton",
         |            "localCopy": $localCopy,
         |            "forwards": [ "${andreUsername.asMailAddress().asString()}"]
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
      .addMapping(MappingSource.fromUser(andreUsername), Mapping.forward(bobUsername.asString()))

    // WHEN Bob Forward/set to forward mails to Andre
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "${bobAccountId}",
         |        "update": {
         |            "singleton": {
         |                "localCopy": false,
         |                "forwards": [
         |                    "${andreUsername.asMailAddress().asString()}"
         |                ]
         |            }
         |        }
         |      }, "c1"],
         |      ["Forward/get", {
         |        "accountId": "${bobAccountId}",
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
         |                "accountId": "${bobAccountId}",
         |                "newState": "${INSTANCE.value}",
         |                "notUpdated": {
         |                    "singleton": {
         |                        "type": "invalidPatch",
         |                        "description": "Creation of redirection of ${bobUsername.asString()} to forward:${andreUsername.asString()} would lead to a loop, operation not performed"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ],
         |        [
         |            "Forward/get",
         |            {
         |                "accountId": "${bobAccountId}",
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
         |        "accountId": "${bobAccountId}",
         |        "update": {
         |            "singleton": {
         |                "localCopy": true,
         |                "forwards": []
         |            }
         |        }
         |      }, "c1"],
         |      ["Forward/get", {
         |        "accountId": "${bobAccountId}",
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
         |      "accountId": "${bobAccountId}",
         |      "newState": "${INSTANCE.value}",
         |      "updated": {"singleton":{}}
         |    }, "c1"],
         |    ["Forward/get", {
         |       "accountId": "${bobAccountId}",
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
  def updateShouldKeepLocalCopyWhenOnlyForwardsIsPatched(): Unit = {
    val response: String = patchAfterInitialForward(
      s"""{ "forwards": [ "${cedricUsername.asMailAddress().asString()}" ] }""")

    assertThatJson(response).isEqualTo(
      expectedPatchResponse(localCopy = true, forwards = s""""${cedricUsername.asMailAddress().asString()}""""))
  }

  @Test
  def updateShouldKeepForwardsWhenOnlyLocalCopyIsPatched(): Unit = {
    val response: String = patchAfterInitialForward("""{ "localCopy": false }""")

    assertThatJson(response).isEqualTo(
      expectedPatchResponse(localCopy = false, forwards = s""""${andreUsername.asMailAddress().asString()}""""))
  }

  @Test
  def updateShouldNoopWhenEmptyPatch(): Unit = {
    val response: String = patchAfterInitialForward("{}")

    assertThatJson(response).isEqualTo(
      expectedPatchResponse(localCopy = true, forwards = s""""${andreUsername.asMailAddress().asString()}""""))
  }

  @Test
  def updateShouldAcceptSingletonId(): Unit = {
    val response: String = patchAfterInitialForward("""{ "id": "singleton", "localCopy": false }""")

    assertThatJson(response).isEqualTo(
      expectedPatchResponse(localCopy = false, forwards = s""""${andreUsername.asMailAddress().asString()}""""))
  }

  @Test
  def updateShouldNoopWhenEmptyPatchAndNoForward(): Unit = {
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "${bobAccountId}",
         |        "update": {
         |            "singleton": {}
         |        }
         |      }, "c1"],
         |      ["Forward/get", {
         |        "accountId": "${bobAccountId}",
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
         |      "accountId": "${bobAccountId}",
         |      "newState": "${INSTANCE.value}",
         |      "updated": {"singleton":{}}
         |    }, "c1"],
         |    ["Forward/get", {
         |       "accountId": "${bobAccountId}",
         |       "notFound": [],
         |       "state": "${INSTANCE.value}",
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
  def updateShouldFailWhenUnknownProperty(): Unit = {
    val response: String = patchAfterInitialForward("""{ "localCopy": false, "unknown": "value" }""")

    assertThatJson(response).isEqualTo(
      expectedPatchFailureResponse("'/unknown' property is not valid: Unknown property"))
  }

  @Test
  def updateShouldFailWhenIdIsNotSingleton(): Unit = {
    val response: String = patchAfterInitialForward("""{ "id": "other", "localCopy": false }""")

    assertThatJson(response).isEqualTo(
      expectedPatchFailureResponse("'/id' property is not valid: id must be singleton"))
  }

  @Test
  def updateShouldFailWhenNullForwards(): Unit = {
    val response: String = patchAfterInitialForward("""{ "forwards": null }""")

    assertThatJson(response).isEqualTo(
      expectedPatchFailureResponse("'/forwards' property is not valid: null is not allowed"))
  }

  @Test
  def updateShouldFailWhenNullLocalCopy(): Unit = {
    val response: String = patchAfterInitialForward("""{ "localCopy": null }""")

    assertThatJson(response).isEqualTo(
      expectedPatchFailureResponse("'/localCopy' property is not valid: null is not allowed"))
  }

  private def patchAfterInitialForward(patch: String): String = {
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "${bobAccountId}",
         |        "update": {
         |            "singleton": {
         |                "localCopy": true,
         |                "forwards": [ "${andreUsername.asMailAddress().asString()}" ]
         |            }
         |        }
         |      }, "c1"],
         |      ["Forward/set", {
         |        "accountId": "${bobAccountId}",
         |        "update": {
         |            "singleton": $patch
         |        }
         |      }, "c2"],
         |      ["Forward/get", {
         |        "accountId": "${bobAccountId}",
         |        "ids": ["singleton"]
         |      }, "c3" ]
         |    ]
         |  }""".stripMargin

    `given`
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
  }

  private def expectedPatchResponse(localCopy: Boolean, forwards: String): String =
    expectedResponseAfterInitialForward(
      patchResult = """"updated": {"singleton":{}}""",
      localCopy = localCopy,
      forwards = forwards)

  private def expectedPatchFailureResponse(description: String): String =
    expectedResponseAfterInitialForward(
      patchResult =
        s""""notUpdated": {
           |  "singleton": {
           |    "type": "invalidArguments",
           |    "description": "$description"
           |  }
           |}""".stripMargin,
      localCopy = true,
      forwards = s""""${andreUsername.asMailAddress().asString()}"""")

  private def expectedResponseAfterInitialForward(patchResult: String, localCopy: Boolean, forwards: String): String =
    s"""{
       |  "sessionState": "${SESSION_STATE.value}",
       |  "methodResponses": [
       |    ["Forward/set", {
       |      "accountId": "${bobAccountId}",
       |      "newState": "${INSTANCE.value}",
       |      "updated": {"singleton":{}}
       |    }, "c1"],
       |    ["Forward/set", {
       |      "accountId": "${bobAccountId}",
       |      "newState": "${INSTANCE.value}",
       |      $patchResult
       |    }, "c2"],
       |    ["Forward/get", {
       |       "accountId": "${bobAccountId}",
       |       "notFound": [],
       |       "state": "${INSTANCE.value}",
       |       "list": [
       |         { "id": "singleton",
       |            "localCopy": $localCopy,
       |            "forwards": [ $forwards ]
       |         }
       |       ]
       |    }, "c3" ]
       |  ]
       |}""".stripMargin

  @Test
  def updateShouldFailWhenInvalidKey(): Unit = {
    val request: String =
      s"""{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "${bobAccountId}",
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
         |      "accountId": "${bobAccountId}",
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
      s"""{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "${bobAccountId}",
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
         |      "accountId": "${bobAccountId}",
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
      s"""{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "${bobAccountId}",
        |        "update": {
        |            "singleton": {
        |                "localCopy": true,
        |                "forwards": [
        |                    "123$$#%$$#invalid"
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
         |      "accountId": "${bobAccountId}",
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
      s"""{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "${bobAccountId}",
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
         |      "accountId": "${bobAccountId}",
         |      "newState": "${INSTANCE.value}"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenMultiplePatchObjects(): Unit = {
    val request: String =
      s"""{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "${bobAccountId}",
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
         |      "accountId": "${bobAccountId}",
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
      s"""{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "${bobAccountId}",
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
         |      "accountId": "${bobAccountId}",
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
      s"""{
        |    "using": [ "urn:ietf:params:jmap:core",
        |               "com:linagora:params:jmap:forward" ],
        |    "methodCalls": [
        |      ["Forward/set", {
        |        "accountId": "${bobAccountId}",
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
         |      "accountId": "${bobAccountId}",
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
         |        "accountId": "${bobAccountId}",
         |        "update": {
         |            "singleton": {
         |                "localCopy": true,
         |                "forwards": [ "${andreUsername.asMailAddress().asString()}"]
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
        .setSender(bobUsername.asString())
        .addToRecipient(bobUsername.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(bobUsername.asString())
      .recipient(bobUsername.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(bobUsername.asString(), BOB_PASSWORD)
      .sendMessage(mail)

    CALMLY_AWAIT.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      assertThat(listAllMessageResult(server, andreUsername)).hasSize(1)
    }
  }

  @Test
  def messageShouldBeForwardedToOwnerWhenLocalCopyIsTrue(server: GuiceJamesServer): Unit = {
    assertThat(listAllMessageResult(server, bobUsername)).hasSize(0)
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "${bobAccountId}",
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
        .setSender(andreUsername.asString())
        .addToRecipient(bobUsername.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(andreUsername.asString())
      .recipient(bobUsername.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(andreUsername.asString(), ANDRE_PASSWORD)
      .sendMessage(mail)

    CALMLY_AWAIT.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      assertThat(listAllMessageResult(server, bobUsername)).hasSize(1)
    }
  }

  @Test
  def messageShouldNOTBeForwardedToOwnerWhenLocalCopyIsFalse(server: GuiceJamesServer): Unit = {
    assertThat(listAllMessageResult(server, bobUsername)).hasSize(0)
    assertThat(listAllMessageResult(server, andreUsername)).hasSize(0)
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "${bobAccountId}",
         |        "update": {
         |            "singleton": {
         |                "localCopy": false,
         |                "forwards": [ "${andreUsername.asMailAddress().asString()}"]
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
        .setSender(cedricUsername.asString())
        .addToRecipient(bobUsername.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(cedricUsername.asString())
      .recipient(bobUsername.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(cedricUsername.asString(), CEDRIC_PASSWORD)
      .sendMessage(mail)

    CALMLY_AWAIT.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      assertThat(listAllMessageResult(server, andreUsername)).hasSize(1)
      assertThat(listAllMessageResult(server, bobUsername)).hasSize(0)
    }
  }

  @Test
  def messageShouldNOTBeForwardedToOtherNotInDestinationForwards(server: GuiceJamesServer): Unit = {
    assertThat(listAllMessageResult(server, bobUsername)).hasSize(0)
    assertThat(listAllMessageResult(server, andreUsername)).hasSize(0)
    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "${bobAccountId}",
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
        .setSender(cedricUsername.asString())
        .addToRecipient(bobUsername.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(cedricUsername.asString())
      .recipient(bobUsername.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(cedricUsername.asString(), CEDRIC_PASSWORD)
      .sendMessage(mail)

    CALMLY_AWAIT.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      assertThat(listAllMessageResult(server, andreUsername)).hasSize(0)
      assertThat(listAllMessageResult(server, bobUsername)).hasSize(1)
    }
  }

  @Test
  def messageShouldBeForwardedToDestinationForwardsAndOwner(server: GuiceJamesServer): Unit = {
    assertThat(listAllMessageResult(server, andreUsername)).hasSize(0)
    assertThat(listAllMessageResult(server, bobUsername)).hasSize(0)

    val request: String =
      s"""{
         |    "using": [ "urn:ietf:params:jmap:core",
         |               "com:linagora:params:jmap:forward" ],
         |    "methodCalls": [
         |      ["Forward/set", {
         |        "accountId": "${bobAccountId}",
         |        "update": {
         |            "singleton": {
         |                "localCopy": true,
         |                "forwards": [ "${andreUsername.asMailAddress().asString()}"]
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
        .setSender(cedricUsername.asString())
        .addToRecipient(bobUsername.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(cedricUsername.asString())
      .recipient(bobUsername.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(cedricUsername.asString(), CEDRIC_PASSWORD)
      .sendMessage(mail)

    CALMLY_AWAIT.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      assertThat(listAllMessageResult(server, andreUsername)).hasSize(1)
      assertThat(listAllMessageResult(server, bobUsername)).hasSize(1)
    }
  }
  @Test
  def setShouldRejectFromDelegatedAccount(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(bobUsername, andreUsername)


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
      .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
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