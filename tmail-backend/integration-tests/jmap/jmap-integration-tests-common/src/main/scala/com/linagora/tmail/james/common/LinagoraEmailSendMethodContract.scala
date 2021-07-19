package com.linagora.tmail.james.common

import com.linagora.tmail.james.common.EncryptHelper.uploadPublicKey
import com.linagora.tmail.james.common.LinagoraEmailSendMethodContract.{BOB_INBOX_PATH, HTML_BODY}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_CREATED
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.draft.MessageIdProbe
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.model.{MailboxConstants, MailboxId, MailboxPath, MessageId, MessageResult, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.{JsString, JsValue, Json}

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

object LinagoraEmailSendMethodContract {
  val MESSAGE: Message = Message.Builder.of
    .setSubject("test")
    .setSender(BOB.asString)
    .setFrom(BOB.asString)
    .setTo(BOB.asString)
    .setBody("test mail", StandardCharsets.UTF_8)
    .build

  val MESSAGE_PREVIEW: String = "test mail"

  val BOB_INBOX_PATH: MailboxPath = MailboxPath.inbox(BOB)
  val HTML_BODY: String = "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>"

}

trait LinagoraEmailSendMethodContract {
  private lazy val slowPacedPollInterval: Duration = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait: ConditionFactory = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds: ConditionFactory = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(BOB_INBOX_PATH)

    uploadPublicKey(ACCOUNT_ID, requestSpecification)
  }

  def randomMessageId: MessageId

  private def buildAndreRequestSpecification(server: GuiceJamesServer): RequestSpecification =
    baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

  private def listAllMessageResult(guiceJamesServer: GuiceJamesServer, username: Username): List[MessageResult] =
    guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
      .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build, username.asString(), 100)
      .asScala
      .flatMap(messageId => guiceJamesServer.getProbe(classOf[MessageIdProbe]).getMessages(messageId, username).asScala.headOption)
      .toList

  private def bobSendsAMailToAndre(server: GuiceJamesServer): String =
    s"""
       |{
       |  "using": [
       |    "urn:ietf:params:jmap:core",
       |    "urn:ietf:params:jmap:mail",
       |    "urn:ietf:params:jmap:submission",
       |    "com:linagora:params:jmap:pgp"
       |  ],
       |  "methodCalls": [
       |    [
       |      "Email/send",
       |      {
       |        "accountId": "$ACCOUNT_ID",
       |        "create": {
       |          "K87": {
       |            "email/create": {
       |              "mailboxIds": {
       |                "${getBobInboxId(server).serialize}": true
       |              },
       |              "subject": "World domination",
       |              "htmlBody": [
       |                {
       |                  "partId": "a49d",
       |                  "type": "text/html"
       |                }
       |              ],
       |              "bodyValues": {
       |                "a49d": {
       |                  "value": "$HTML_BODY",
       |                  "isTruncated": false,
       |                  "isEncodingProblem": false
       |                }
       |              }
       |            },
       |            "emailSubmission/set": {
       |              "envelope": {
       |                "mailFrom": {
       |                  "email": "${BOB.asString}"
       |                },
       |                "rcptTo": [
       |                  {
       |                    "email": "${ANDRE.asString}"
       |                  }
       |                ]
       |              }
       |            }
       |          }
       |        }
       |      },
       |      "c1"
       |    ]
       |  ]
       |}""".stripMargin

  private def getBobInboxId(server: GuiceJamesServer): MailboxId =
    server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(MailboxConstants.USER_NAMESPACE, BOB.asString, MailboxConstants.INBOX)

  @Test
  def emailSendShouldReturnSuccess(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(ANDRE))

    val response: String = `given`
      .body(bobSendsAMailToAndre(server))
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""
           |{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Email/send",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "newState": "$${json-unit.ignore}",
           |                "created": {
           |                    "K87": {
           |                        "emailSubmissionId": "$${json-unit.ignore}",
           |                        "emailId": "$${json-unit.ignore}",
           |                        "blobId": "$${json-unit.ignore}",
           |                        "threadId": "$${json-unit.ignore}",
           |                        "size": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSendShouldAddAnEmailInTargetMailbox(server: GuiceJamesServer) : Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(ANDRE))

    `given`
      .body(bobSendsAMailToAndre(server))
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    awaitAtMostTenSeconds.untilAsserted { () =>
      val listBobMessageResult: List[MessageResult] = listAllMessageResult(server, BOB )
      assertThat(listBobMessageResult.size).
        isEqualTo(1)
      assertThat(listBobMessageResult.head.getMailboxId)
        .isEqualTo(getBobInboxId(server))
    }
  }

  @Test
  def emailSendShouldSendMailSuccessfully(server: GuiceJamesServer): Unit = {
    val andreInboxId : MailboxId= server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(ANDRE))

    `given`
      .body(bobSendsAMailToAndre(server))
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    awaitAtMostTenSeconds.untilAsserted { () =>
      val listAndreMessageResult: List[MessageResult] = listAllMessageResult(server, ANDRE )

      assertThat(listAndreMessageResult.size)
        .isEqualTo(1)

      assertThat(listAndreMessageResult.head.getMailboxId)
        .isEqualTo(andreInboxId)
    }
  }

  @Test
  def recipientShouldReceiveClearEmailContentWhenTheyDoNotHaveEncryptionEnabled(server: GuiceJamesServer) : Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(ANDRE))

    `given`
      .body(bobSendsAMailToAndre(server))
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    awaitAtMostTenSeconds.untilAsserted { () =>
      val listAndreMessageResult: List[MessageResult] = listAllMessageResult(server, ANDRE )

      assertThat(listAndreMessageResult.nonEmpty)
        .isEqualTo(true)

      val andreRequest: String =
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Email/get",
           |    {
           |      "accountId": "${ANDRE_ACCOUNT_ID}",
           |      "ids": ["${listAndreMessageResult.head.getMessageId.serialize()}"],
           |      "fetchAllBodyValues": true
           |    },
           |    "c1"]]
           |}""".stripMargin

      val andreResponse: String =
        `given`(buildAndreRequestSpecification(server))
          .body(andreRequest)
        .when()
          .post()
        .`then`
          .statusCode(HttpStatus.SC_OK)
          .contentType(JSON)
          .extract()
          .body()
          .asString()

        assertThatJson(andreResponse)
          .inPath("methodResponses[0][1].list[0].bodyValues")
          .isEqualTo(
            s"""{
               |    "2": {
               |        "value": "$HTML_BODY",
               |        "isEncodingProblem": false,
               |        "isTruncated": false
               |    },
               |    "3": {
               |        "value": "I have the most brilliant plan. Let me tell you all about it. What we do is, we",
               |        "isEncodingProblem": false,
               |        "isTruncated": false
               |    }
               |}""".stripMargin)
      }
  }

  @Test
  def emailSendShouldSuccessWhenMixed(server: GuiceJamesServer) : Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(ANDRE))
    val request: String =
      s"""{
         |    "using": [
         |        "urn:ietf:params:jmap:core",
         |        "urn:ietf:params:jmap:mail",
         |        "urn:ietf:params:jmap:submission",
         |        "com:linagora:params:jmap:pgp"
         |    ],
         |    "methodCalls": [
         |        [
         |            "Email/send",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "create": {
         |                    "K87": {
         |                        "email/create": {
         |                            "mailboxIds": {
         |                                "${getBobInboxId(server).serialize}": true
         |                            },
         |                            "subject": "World domination",
         |                            "htmlBody": [
         |                                {
         |                                    "partId": "a49d",
         |                                    "type": "text/html"
         |                                }
         |                            ],
         |                            "bodyValues": {
         |                                "a49d": {
         |                                    "value": "$HTML_BODY",
         |                                    "isTruncated": false,
         |                                    "isEncodingProblem": false
         |                                }
         |                            }
         |                        },
         |                        "emailSubmission/set": {
         |                            "envelope": {
         |                                "mailFrom": {
         |                                    "email": "${BOB.asString}"
         |                                },
         |                                "rcptTo": [
         |                                    {
         |                                        "email": "${ANDRE.asString}"
         |                                    }
         |                                ]
         |                            }
         |                        }
         |                    },
         |                    "K88": {
         |                        "email/create": {
         |                            "mailboxIds": {
         |                                "${getBobInboxId(server).serialize}": true
         |                            },
         |                            "subject": "World domination 88"
         |                        },
         |                        "emailSubmission/set": {
         |                            "envelope": {
         |                                "mailFrom": {
         |                                    "email": "${BOB.asString}"
         |                                },
         |                                "rcptTo": [
         |                                    {
         |                                        "email": "${ANDRE.asString}"
         |                                    }
         |                                ]
         |                            }
         |                        }
         |                    },
         |                    "K89": {
         |                        "email/create": {
         |                            "mailboxIds": {
         |                                "${getBobInboxId(server).serialize}": true
         |                            },
         |                            "subject": "World domination 89"
         |                        },
         |                        "emailSubmission/set": {
         |                            "envelope": {
         |                                "mailFrom": {
         |                                    "email": "${BOB.asString}"
         |                                },
         |                                "rcptTo": [
         |                                    {
         |                                        "email": "${ANDRE.asString}"
         |                                    }
         |                                ]
         |                            }
         |                        }
         |                    }
         |                },
         |                "onSuccessUpdateEmail": {
         |                    "#K87": {
         |                        "keywords": {
         |                            "$$sent": true
         |                        }
         |                    }
         |                },
         |                "onSuccessDestroyEmail": ["#K88", "#K89"]
         |            },
         |            "c1"
         |        ]
         |    ]
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

    val messageIds: collection.Seq[JsValue] = ((Json.parse(response)
      \\ "methodResponses")
      .head \\ "created" )
      .head  \\ "emailId"
    val k87MessageId: String = messageIds.head.asInstanceOf[JsString].value
    val k88MessageId: String = messageIds(1).asInstanceOf[JsString].value
    val k89MessageId: String = messageIds(2).asInstanceOf[JsString].value

    assertThatJson(response)
      .when(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Email/send",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "newState": "$${json-unit.ignore}",
           |                "created": {
           |                    "K87": {
           |                        "emailSubmissionId": "$${json-unit.ignore}",
           |                        "emailId": "$${json-unit.ignore}",
           |                        "blobId": "$${json-unit.ignore}",
           |                        "threadId": "$${json-unit.ignore}",
           |                        "size": "$${json-unit.ignore}"
           |                    },
           |                    "K88": {
           |                        "emailSubmissionId": "$${json-unit.ignore}",
           |                        "emailId": "$${json-unit.ignore}",
           |                        "blobId": "$${json-unit.ignore}",
           |                        "threadId": "$${json-unit.ignore}",
           |                        "size": "$${json-unit.ignore}"
           |                    },
           |                    "K89": {
           |                        "emailSubmissionId": "$${json-unit.ignore}",
           |                        "emailId": "$${json-unit.ignore}",
           |                        "blobId": "$${json-unit.ignore}",
           |                        "threadId": "$${json-unit.ignore}",
           |                        "size": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Email/set",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "oldState": "$${json-unit.ignore}",
           |                "newState": "$${json-unit.ignore}",
           |                "updated": {
           |                    "$k87MessageId": null
           |                },
           |                "destroyed": [
           |                    "$k88MessageId",
           |                    "$k89MessageId"
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSendShouldSendMailSuccessfullyToSelf(server: GuiceJamesServer): Unit = {
    val bobDraftMailBoxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox( MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS))

    val request: String =
      s"""
         |{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:submission",
         |    "com:linagora:params:jmap:pgp"
         |  ],
         |  "methodCalls": [
         |    [
         |      "Email/send",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "K87": {
         |            "email/create": {
         |              "mailboxIds": {
         |                "${bobDraftMailBoxId.serialize}": true
         |              },
         |              "subject": "World domination",
         |              "htmlBody": [
         |                {
         |                  "partId": "a49d",
         |                  "type": "text/html"
         |                }
         |              ],
         |              "bodyValues": {
         |                "a49d": {
         |                  "value": "$HTML_BODY",
         |                  "isTruncated": false,
         |                  "isEncodingProblem": false
         |                }
         |              }
         |            },
         |            "emailSubmission/set": {
         |              "envelope": {
         |                "mailFrom": {
         |                  "email": "${BOB.asString}"
         |                },
         |                "rcptTo": [
         |                  {
         |                    "email": "${BOB.asString}"
         |                  }
         |                ]
         |              }
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    val bobInboxId: MailboxId = getBobInboxId(server)
    awaitAtMostTenSeconds.untilAsserted { () =>
      assertThat(listAllMessageResult(server, BOB)
        .map(result => result.getMailboxId)
        .asJava)
        .containsExactlyInAnyOrder(bobDraftMailBoxId, bobInboxId)
    }
  }

  @Test
  def emailSendShouldSendMailSuccessfullyToBothRecipients(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .addUser(CEDRIC.asString(), "cedricPassword")

    val cedricInBoxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
    .createMailbox(MailboxPath.inbox(CEDRIC))
    val andreInboxId : MailboxId= server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(ANDRE))

    val request: String =
      s"""
         |{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:submission",
         |    "com:linagora:params:jmap:pgp"
         |  ],
         |  "methodCalls": [
         |    [
         |      "Email/send",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "K87": {
         |            "email/create": {
         |              "mailboxIds": {
         |                "${getBobInboxId(server).serialize}": true
         |              },
         |              "subject": "World domination",
         |              "htmlBody": [
         |                {
         |                  "partId": "a49d",
         |                  "type": "text/html"
         |                }
         |              ],
         |              "bodyValues": {
         |                "a49d": {
         |                  "value": "$HTML_BODY",
         |                  "isTruncated": false,
         |                  "isEncodingProblem": false
         |                }
         |              }
         |            },
         |            "emailSubmission/set": {
         |              "envelope": {
         |                "mailFrom": {
         |                  "email": "${BOB.asString}"
         |                },
         |                "rcptTo": [
         |                  {
         |                    "email": "${ANDRE.asString}"
         |                  },
         |                  {
         |                    "email": "${CEDRIC.asString}"
         |                  }
         |                ]
         |              }
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
         |  ]
         |}""".stripMargin

    `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    awaitAtMostTenSeconds.untilAsserted { () =>
      val listCedricMessageResult: List[MessageResult] = listAllMessageResult(server, CEDRIC )
      val listAndreMessageResult: List[MessageResult] = listAllMessageResult(server, ANDRE )

      assertSoftly(softly => {
        softly.assertThat(listCedricMessageResult.size)
          .isEqualTo(1)
        softly.assertThat(listAndreMessageResult.size)
          .isEqualTo(1)
      })
    }
  }

  @Test
  def emailSendShouldNotCreatedWhenMailboxIdInvalid(): Unit = {
    val request: String =
      s"""
         |{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:submission",
         |    "com:linagora:params:jmap:pgp"
         |  ],
         |  "methodCalls": [
         |    [
         |      "Email/send",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "K87": {
         |            "email/create": {
         |              "mailboxIds": {
         |                "invalid": true
         |              },
         |              "subject": "World domination",
         |              "htmlBody": [
         |                {
         |                  "partId": "a49d",
         |                  "type": "text/html"
         |                }
         |              ],
         |              "bodyValues": {
         |                "a49d": {
         |                  "value": "$HTML_BODY",
         |                  "isTruncated": false,
         |                  "isEncodingProblem": false
         |                }
         |              }
         |            },
         |            "emailSubmission/set": {
         |              "envelope": {
         |                "mailFrom": {
         |                  "email": "${BOB.asString}"
         |                },
         |                "rcptTo": [
         |                  {
         |                    "email": "${ANDRE.asString}"
         |                  }
         |                ]
         |              }
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
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

    assertThatJson(response)
      .isEqualTo(
        s"""
           |{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Email/send",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "newState": "$${json-unit.ignore}",
           |                "notCreated": {
           |                    "K87": {
           |                        "type": "invalidArguments",
           |                        "description": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    val errorDescription: String = ((Json.parse(response)
      \\ "methodResponses")
      .head \\ "description" )
      .head.asInstanceOf[JsString].value

    assertThat(errorDescription)
      .contains("'/mailboxIds/invalid' property in EmailSend object is not valid")
  }

  @Test
  def emailSendShouldNotCreatedWhenEmailCreateRequestCanNotDeserialize(server: GuiceJamesServer) : Unit = {
    val request: String =
      s"""
         |{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:submission",
         |    "com:linagora:params:jmap:pgp"
         |  ],
         |  "methodCalls": [
         |    [
         |      "Email/send",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "K87": {
         |            "email/create": {
         |              "mailboxIds": {
         |                 "${getBobInboxId(server).serialize}": true
         |              },
         |              "unknownProperty": "World domination"
         |            },
         |            "emailSubmission/set": {
         |              "envelope": {
         |                "mailFrom": {
         |                  "email": "${BOB.asString}"
         |                },
         |                "rcptTo": [
         |                  {
         |                    "email": "${ANDRE.asString}"
         |                  }
         |                ]
         |              }
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
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

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Email/send",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "newState": "$${json-unit.ignore}",
           |                "notCreated": {
           |                    "K87": {
           |                        "type": "invalidArguments",
           |                        "description": "Some unknown properties were specified",
           |                        "properties": [
           |                            "unknownProperty"
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
  def emailSendShouldNotCreatedWhenEmailSubmissionSetRequestCanNotDeserialize(server: GuiceJamesServer) : Unit = {
    val request: String =
      s"""
         |{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:submission",
         |    "com:linagora:params:jmap:pgp"
         |  ],
         |  "methodCalls": [
         |    [
         |      "Email/send",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "K87": {
         |            "email/create": {
         |              "mailboxIds": {
         |                 "${getBobInboxId(server).serialize}": true
         |              },
         |              "subject": "World domination"
         |            },
         |            "emailSubmission/set": {
         |              "unknownProperty": "unknown",
         |              "envelope": {
         |                "mailFrom": {
         |                  "email": "${BOB.asString}"
         |                },
         |                "rcptTo": [
         |                  {
         |                    "email": "${ANDRE.asString}"
         |                  }
         |                ]
         |              }
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
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

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Email/send",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "newState": "$${json-unit.ignore}",
           |                "notCreated": {
           |                    "K87": {
           |                        "type": "invalidArguments",
           |                        "description": "Some unknown properties were specified",
           |                        "properties": [
           |                            "unknownProperty"
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
  def envelopeShouldBeOptionalWhenEmailCreateAlreadyDeclare(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(ANDRE))

    val request: String =
      s"""
       |{
       |  "using": [
       |    "urn:ietf:params:jmap:core",
       |    "urn:ietf:params:jmap:mail",
       |    "urn:ietf:params:jmap:submission",
       |    "com:linagora:params:jmap:pgp"
       |  ],
       |  "methodCalls": [
       |    [
       |      "Email/send",
       |      {
       |        "accountId": "$ACCOUNT_ID",
       |        "create": {
       |          "K87": {
       |            "email/create": {
       |              "mailboxIds": {
       |                "${getBobInboxId(server).serialize}": true
       |              },
       |              "subject": "World domination",
       |              "to": [{"email": "${ANDRE.asString()}"}],
       |              "from": [{"email": "${BOB.asString()}"}],
       |              "sender": [{"email": "${BOB.asString()}"}],
       |              "htmlBody": [
       |                {
       |                  "partId": "a49d",
       |                  "type": "text/html"
       |                }
       |              ],
       |              "bodyValues": {
       |                "a49d": {
       |                  "value": "$HTML_BODY",
       |                  "isTruncated": false,
       |                  "isEncodingProblem": false
       |                }
       |              }
       |            },
       |            "emailSubmission/set": {}
       |          }
       |        }
       |      },
       |      "c1"
       |    ]
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

    assertThatJson(response)
      .isEqualTo(
        s"""
           |{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Email/send",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "newState": "$${json-unit.ignore}",
           |                "created": {
           |                    "K87": {
           |                        "emailSubmissionId": "$${json-unit.ignore}",
           |                        "emailId": "$${json-unit.ignore}",
           |                        "blobId": "$${json-unit.ignore}",
           |                        "threadId": "$${json-unit.ignore}",
           |                        "size": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSendShouldNotCreatedWhenEnvelopAbsentAndEmailSetDoNotHaveHeader(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(ANDRE))

    val request: String =
      s"""
         |{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:submission",
         |    "com:linagora:params:jmap:pgp"
         |  ],
         |  "methodCalls": [
         |    [
         |      "Email/send",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "K87": {
         |            "email/create": {
         |              "mailboxIds": {
         |                "${getBobInboxId(server).serialize}": true
         |              },
         |              "subject": "World domination",
         |              "htmlBody": [
         |                {
         |                  "partId": "a49d",
         |                  "type": "text/html"
         |                }
         |              ],
         |              "bodyValues": {
         |                "a49d": {
         |                  "value": "$HTML_BODY",
         |                  "isTruncated": false,
         |                  "isEncodingProblem": false
         |                }
         |              }
         |            },
         |            "emailSubmission/set": {}
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
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

    assertThatJson(response)
      .isEqualTo(
        s"""
           |{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Email/send",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "newState": "$${json-unit.ignore}",
           |                "notCreated": {
           |                    "K87": {
           |                        "type": "serverFail",
           |                        "description": "Implicit envelope detection requires a from field"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

  }

  @Test
  def emailSendShouldSupportAttachment(server: GuiceJamesServer) : Unit = {
    val payload: Array[Byte] = "123456789\r\n".getBytes(StandardCharsets.UTF_8)

    val uploadResponse: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID/")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .asString

    val blobId: String = Json.parse(uploadResponse).\("blobId").get.asInstanceOf[JsString].value

    val request: String =
      s"""
         |{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:submission",
         |    "com:linagora:params:jmap:pgp"
         |  ],
         |  "methodCalls": [
         |    [
         |      "Email/send",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "K87": {
         |            "email/create": {
         |              "mailboxIds": {
         |                "${getBobInboxId(server).serialize}": true
         |              },
         |              "subject": "World domination",
         |              "attachments": [
         |               {
         |                  "blobId": "$blobId",
         |                  "type":"text/plain",
         |                  "charset":"UTF-8",
         |                  "disposition": "attachment"
         |                }
         |              ],
         |              "htmlBody": [
         |                {
         |                  "partId": "a49d",
         |                  "type": "text/html"
         |                }
         |              ],
         |              "bodyValues": {
         |                "a49d": {
         |                  "value": "$HTML_BODY",
         |                  "isTruncated": false,
         |                  "isEncodingProblem": false
         |                }
         |              }
         |            },
         |            "emailSubmission/set": {
         |              "envelope": {
         |                "mailFrom": {
         |                  "email": "${BOB.asString}"
         |                },
         |                "rcptTo": [
         |                  {
         |                    "email": "${ANDRE.asString}"
         |                  }
         |                ]
         |              }
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
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

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Email/send",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "newState": "$${json-unit.ignore}",
           |                "created": {
           |                    "K87": {
           |                        "emailSubmissionId": "$${json-unit.ignore}",
           |                        "emailId": "$${json-unit.ignore}",
           |                        "blobId": "$${json-unit.ignore}",
           |                        "threadId": "$${json-unit.ignore}",
           |                        "size": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  //region onSuccessUpdateEmail & onSuccessDestroyEmail checking
  @Test
  def onSuccessUpdateEmailShouldTriggerAnImplicitEmailSetCall(server: GuiceJamesServer): Unit = {
    val request: String =
      s"""{
         |    "using": [
         |        "urn:ietf:params:jmap:core",
         |        "urn:ietf:params:jmap:mail",
         |        "urn:ietf:params:jmap:submission",
         |        "com:linagora:params:jmap:pgp"
         |    ],
         |    "methodCalls": [
         |        [
         |            "Email/send",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "create": {
         |                    "K87": {
         |                        "email/create": {
         |                            "mailboxIds": {
         |                                "${getBobInboxId(server).serialize}": true
         |                            },
         |                            "subject": "World domination",
         |                            "htmlBody": [
         |                                {
         |                                    "partId": "a49d",
         |                                    "type": "text/html"
         |                                }
         |                            ],
         |                            "bodyValues": {
         |                                "a49d": {
         |                                    "value": "$HTML_BODY",
         |                                    "isTruncated": false,
         |                                    "isEncodingProblem": false
         |                                }
         |                            }
         |                        },
         |                        "emailSubmission/set": {
         |                            "envelope": {
         |                                "mailFrom": {
         |                                    "email": "${BOB.asString}"
         |                                },
         |                                "rcptTo": [
         |                                    {
         |                                        "email": "${ANDRE.asString}"
         |                                    }
         |                                ]
         |                            }
         |                        }
         |                    }
         |                },
         |                "onSuccessUpdateEmail": {
         |                    "#K87": {
         |                        "keywords": {
         |                            "$$sent": true
         |                        }
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
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

    val messageIdResult: String = ((Json.parse(response)
      \\ "methodResponses")
      .head \\ "emailId" )
      .head.asInstanceOf[JsString].value

    assertThatJson(response)
      .inPath("methodResponses[1]")
      .isEqualTo(
        s"""
           |[
           |    "Email/set",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "oldState": "$${json-unit.ignore}",
           |        "newState": "$${json-unit.ignore}",
           |        "updated": {
           |            "$messageIdResult": null
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def emailSendShouldFailWhenOnSuccessUpdateEmailMissesTheCreationIdSharp(): Unit = {
    val request: String =
      s"""
         |{
         |  "using": ["urn:ietf:params:jmap:core",
         |            "urn:ietf:params:jmap:mail",
         |            "urn:ietf:params:jmap:submission",
         |            "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["Email/send", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K87": {
         |            "email/create": {
         |            },
         |            "emailSubmission/set": {
         |            }
         |        }
         |      },
         |      "onSuccessUpdateEmail": {
         |        "notStored": {
         |          "keywords": {"$$sent":true}
         |        }
         |      }
         |    },
         |
         |     "c1"]
         |  ]
         |}
         |""".stripMargin

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

    assertThatJson(response)
      .isEqualTo(s"""{
                    |    "sessionState": "${SESSION_STATE.value}",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments",
                    |                "description": "notStored cannot be retrieved as storage for Email/send is not yet implemented"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def emailSendShouldFailWhenOnSuccessUpdateEmailDoesNotReferenceACreationWithinThisCall(): Unit = {
    val request: String =
      s"""
         |{
         |  "using": ["urn:ietf:params:jmap:core",
         |            "urn:ietf:params:jmap:mail",
         |            "urn:ietf:params:jmap:submission",
         |            "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["Email/send", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K87": {
         |            "email/create": {
         |            },
         |            "emailSubmission/set": {
         |            }
         |        }
         |      },
         |      "onSuccessUpdateEmail": {
         |        "#badReference": {
         |          "keywords": {"$$sent":true}
         |        }
         |      }
         |    },
         |
         |     "c1"]
         |  ]
         |}
         |""".stripMargin

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

    assertThatJson(response)
      .isEqualTo(s"""{
                    |    "sessionState": "${SESSION_STATE.value}",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments",
                    |                "description": "#badReference cannot be referenced in current method call"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def onSuccessDestroyEmailShouldTriggerAnImplicitEmailSetCall(server: GuiceJamesServer): Unit = {
    val request: String =
      s"""{
         |    "using": [
         |        "urn:ietf:params:jmap:core",
         |        "urn:ietf:params:jmap:mail",
         |        "urn:ietf:params:jmap:submission",
         |        "com:linagora:params:jmap:pgp"
         |    ],
         |    "methodCalls": [
         |        [
         |            "Email/send",
         |            {
         |                "accountId": "$ACCOUNT_ID",
         |                "create": {
         |                    "K87": {
         |                        "email/create": {
         |                            "mailboxIds": {
         |                                "${getBobInboxId(server).serialize}": true
         |                            },
         |                            "subject": "World domination",
         |                            "htmlBody": [
         |                                {
         |                                    "partId": "a49d",
         |                                    "type": "text/html"
         |                                }
         |                            ],
         |                            "bodyValues": {
         |                                "a49d": {
         |                                    "value": "$HTML_BODY",
         |                                    "isTruncated": false,
         |                                    "isEncodingProblem": false
         |                                }
         |                            }
         |                        },
         |                        "emailSubmission/set": {
         |                            "envelope": {
         |                                "mailFrom": {
         |                                    "email": "${BOB.asString}"
         |                                },
         |                                "rcptTo": [
         |                                    {
         |                                        "email": "${ANDRE.asString}"
         |                                    }
         |                                ]
         |                            }
         |                        }
         |                    }
         |                },
         |                "onSuccessDestroyEmail": ["#K87"]
         |            },
         |            "c1"
         |        ]
         |    ]
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

    val messageIdResult: String = ((Json.parse(response)
      \\ "methodResponses")
      .head \\ "emailId" )
      .head.asInstanceOf[JsString].value

    assertThatJson(response)
      .inPath("methodResponses[1]")
      .isEqualTo(
        s"""
           |[
           |    "Email/set",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "oldState": "$${json-unit.ignore}",
           |        "newState": "$${json-unit.ignore}",
           |        "destroyed": ["$messageIdResult"]
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def emailSendShouldFailWhenOnSuccessDestroyEmailMissesTheCreationIdSharp(): Unit = {
    val request: String =
      s"""
         |{
         |  "using": ["urn:ietf:params:jmap:core",
         |            "urn:ietf:params:jmap:mail",
         |            "urn:ietf:params:jmap:submission",
         |            "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["Email/send", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K87": {
         |            "email/create": {
         |            },
         |            "emailSubmission/set": {
         |            }
         |        }
         |      },
         |      "onSuccessDestroyEmail": ["notFound"]
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

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

    assertThatJson(response)
      .isEqualTo(s"""{
                    |    "sessionState": "${SESSION_STATE.value}",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments",
                    |                "description": "notFound cannot be retrieved as storage for Email/send is not yet implemented"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def emailSendShouldFailWhenOnSuccessDestroyEmailDoesNotReferenceACreationWithinThisCall(): Unit = {
    val request: String =
      s"""
         |{
         |  "using": ["urn:ietf:params:jmap:core",
         |            "urn:ietf:params:jmap:mail",
         |            "urn:ietf:params:jmap:submission",
         |            "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["Email/send", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K87": {
         |            "email/create": {
         |            },
         |            "emailSubmission/set": {
         |            }
         |        }
         |      },
         |      "onSuccessDestroyEmail": ["#notReference"]
         |    }, "c1"]
         |  ]
         |}
         |""".stripMargin

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

    assertThatJson(response)
      .isEqualTo(s"""{
                    |    "sessionState": "${SESSION_STATE.value}",
                    |    "methodResponses": [
                    |        [
                    |            "error",
                    |            {
                    |                "type": "invalidArguments",
                    |                "description": "#notReference cannot be referenced in current method call"
                    |            },
                    |            "c1"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def implicitSetShouldNotBeAttemptedWhenNotSpecified(server: GuiceJamesServer): Unit = {
    val request : String =
      s"""
         |{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:ietf:params:jmap:submission",
         |    "com:linagora:params:jmap:pgp"
         |  ],
         |  "methodCalls": [
         |    [
         |      "Email/send",
         |      {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "K87": {
         |            "email/create": {
         |              "mailboxIds": {
         |                "${getBobInboxId(server).serialize}": true
         |              },
         |              "subject": "World domination",
         |              "htmlBody": [
         |                {
         |                  "partId": "a49d",
         |                  "type": "text/html"
         |                }
         |              ],
         |              "bodyValues": {
         |                "a49d": {
         |                  "value": "$HTML_BODY",
         |                  "isTruncated": false,
         |                  "isEncodingProblem": false
         |                }
         |              }
         |            },
         |            "emailSubmission/set": {
         |              "envelope": {
         |                "mailFrom": {
         |                  "email": "${BOB.asString}"
         |                },
         |                "rcptTo": [
         |                  {
         |                    "email": "${ANDRE.asString}"
         |                  }
         |                ]
         |              }
         |            }
         |          }
         |        }
         |      },
         |      "c1"
         |    ]
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

    assertThatJson(response)
      .inPath("methodResponses[1]")
      .isAbsent()
  }
  //endregion

  //region basic valid request
  @Test
  def methodShouldReturnFailWhenMissingOneCapability(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [
         |    ["Email/send", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K87": {
         |            "email/create": {
         |            },
         |            "emailSubmission/set": {
         |            }
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:mail, urn:ietf:params:jmap:submission, com:linagora:params:jmap:pgp"
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
         |    ["Email/send", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K87": {
         |            "email/create": {
         |            },
         |            "emailSubmission/set": {
         |            }
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, urn:ietf:params:jmap:mail, urn:ietf:params:jmap:submission, com:linagora:params:jmap:pgp"
         |    },"c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldFailWhenWrongAccountId(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core",
         |            "urn:ietf:params:jmap:mail",
         |            "urn:ietf:params:jmap:submission",
         |            "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["Email/send", {
         |      "accountId": "unknownAccountId",
         |      "create": {
         |        "K87": {
         |            "email/create": {
         |            },
         |            "emailSubmission/set": {
         |            }
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
  def methodShouldRejectOtherAccountIds(server: GuiceJamesServer) : Unit = {

  }

  @Test
  def methodShouldRejectExtraProperties(server: GuiceJamesServer) : Unit = {

  }
  //endregion
}
