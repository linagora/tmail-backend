package com.linagora.tmail.james.common

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.{BodyPartBuilder, MultipartBuilder}
import org.apache.james.modules.{ACLProbeImpl, MailboxProbeImpl}
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.Json

trait LinagoraCalendarEventParseMethodContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  def randomBlobId: String

  @Test
  def shouldReturnCapabilityInSessionRoute(): Unit = {
    val response: String = `given`()
      .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThat(response).contains("\"com:linagora:params:calendar:event\":{}")
  }

  @Test
  def parseShouldSucceed(): Unit = {
    val blobId: String = uploadAndGetBlobId(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"))

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/parse",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "parsed": {
           |            "$blobId": {
           |                "uid": "ea127690-0440-404b-af98-9823c855a283",
           |                "title": "Sprint planning #23",
           |                "description": "description 123",
           |                "start": "2017-01-11T16:00:00",
           |                "end": "2017-01-11T17:30:00",
           |                "utcStart":"2017-01-11T09:00:00Z",
           |                "utcEnd":"2017-01-11T10:30:00Z",
           |                "timeZone": "Asia/Ho_Chi_Minh",
           |                "duration": "PT1H30M",
           |                "location": "Hangout",
           |                "method": "REQUEST",
           |                "sequence": 0,
           |                "priority": 5,
           |                "freeBusyStatus": "busy",
           |                "privacy": "public",
           |                "organizer": {
           |                     "name": "Raphael OUAZANA",
           |                     "mailto": "ouazana@domain.tld"
           |                },
           |                "participants": [
           |                 {
           |                     "name": "Matthieu EXT_BAECHLER",
           |                     "mailto": "baechler@domain.tld",
           |                     "kind": "INDIVIDUAL",
           |                     "role": "ADMIN",
           |                     "participationStatus": "NEEDS-ACTION",
           |                     "expectReply": true
           |                 },
           |                 {
           |                     "name": "Laura ROYET",
           |                     "mailto": "royet@domain.tld",
           |                     "kind": "INDIVIDUAL",
           |                     "participationStatus": "NEEDS-ACTION",
           |                     "expectReply": true
           |                 }
           |             ],
           |             "extensionFields": {
           |                     "X-OPENPAAS-VIDEOCONFERENCE": ["https://jitsi.linagora.com/abcd"]
           |                 },
           |             "recurrenceRules":
           |               [{    "frequency": "yearly",
           |                     "byDay": [ "mo" ],
           |                     "byMonth": [ "10" ],
           |                     "bySetPosition": [ 1, 2 ],
           |                     "until":"2024-01-11T09:00:00Z"
           |               }]
           |           }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def parseShouldSupportSeveralBlobIds(): Unit = {
    val blobId1: String = uploadAndGetBlobId(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"))
    val blobId2: String = uploadAndGetBlobId(ClassLoader.getSystemResourceAsStream("ics/meeting2.ics"))

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "blobIds": [ "$blobId1", "$blobId2" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/parse",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "parsed": {
           |            "$blobId1": {
           |                "uid": "ea127690-0440-404b-af98-9823c855a283",
           |                "title": "Sprint planning #23",
           |                "description": "description 123",
           |                "start": "2017-01-11T16:00:00",
           |                "end": "2017-01-11T17:30:00",
           |                "utcStart": "2017-01-11T09:00:00Z",
           |                "utcEnd": "2017-01-11T10:30:00Z",
           |                "timeZone": "Asia/Ho_Chi_Minh",
           |                "duration": "PT1H30M",
           |                "location": "Hangout",
           |                "method": "REQUEST",
           |                "sequence": 0,
           |                "priority": 5,
           |                "freeBusyStatus": "busy",
           |                "privacy": "public",
           |                "organizer": {
           |                    "name": "Raphael OUAZANA",
           |                    "mailto": "ouazana@domain.tld"
           |                },
           |                "participants": [
           |                    {
           |                        "name": "Matthieu EXT_BAECHLER",
           |                        "mailto": "baechler@domain.tld",
           |                        "kind": "INDIVIDUAL",
           |                        "role": "ADMIN",
           |                        "participationStatus": "NEEDS-ACTION",
           |                        "expectReply": true
           |                    },
           |                    {
           |                        "name": "Laura ROYET",
           |                        "mailto": "royet@domain.tld",
           |                        "kind": "INDIVIDUAL",
           |                        "participationStatus": "NEEDS-ACTION",
           |                        "expectReply": true
           |                    }
           |                ],
           |                "extensionFields": {
           |                    "X-OPENPAAS-VIDEOCONFERENCE": [
           |                        "https://jitsi.linagora.com/abcd"
           |                    ]
           |                },
           |                "recurrenceRules":
           |                   [{  "frequency": "yearly",
           |                       "byDay": [ "mo" ],
           |                       "byMonth": [ "10" ],
           |                       "bySetPosition": [ 1, 2 ],
           |                       "until":"2024-01-11T09:00:00Z"
           |                   }]
           |            },
           |            "$blobId2": {
           |                "uid": "ea127690-0440-404b-af98-9823c855a284",
           |                "title": "Sprint planning #24",
           |                "description": "description 456",
           |                "start": "2017-01-11T09:00:00",
           |                "end": "2017-01-11T10:30:00",
           |                "utcStart": "2017-01-11T09:00:00Z",
           |                "utcEnd": "2017-01-11T10:30:00Z",
           |                "duration": "PT1H30M",
           |                "location": "Hangout",
           |                "method": "CANCEL",
           |                "sequence": 0,
           |                "priority": 5,
           |                "freeBusyStatus": "free",
           |                "privacy": "private",
           |                "organizer": {
           |                    "name": "Raphael OUAZANA",
           |                    "mailto": "ouazana@domain.tld"
           |                },
           |                "participants": [
           |                    {
           |                        "name": "Matthieu EXT_BAECHLER",
           |                        "mailto": "baechler@domain.tld",
           |                        "kind": "INDIVIDUAL",
           |                        "participationStatus": "NEEDS-ACTION",
           |                        "expectReply": true
           |                    },
           |                    {
           |                        "name": "Laura ROYET",
           |                        "mailto": "royet@domain.tld",
           |                        "kind": "INDIVIDUAL",
           |                        "participationStatus": "NEEDS-ACTION",
           |                        "expectReply": true
           |                    }
           |                ],
           |                "extensionFields": {
           |                    "X-OPENPAAS-TEST": [
           |                        "test1",
           |                        "test2"
           |                    ],
           |                    "X-OPENPAAS-VIDEOCONFERENCE": [
           |                        "https://jitsi.linagora.com/abcd"
           |                    ]
           |                },
           |                "recurrenceRules": []
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def parseShouldReturnNotFoundResultWhenBlobIdDoesNotExist(): Unit = {
    val notFoundBlobId: String = randomBlobId

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "blobIds": [ "$notFoundBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/parse",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "notFound": [ "$notFoundBlobId" ]
           |    }, "c1"
           |]""".stripMargin)
  }

  @Test
  def parseShouldReturnNotParseableWhenNotAnICS(): Unit = {
    val notParsableBlobId: String = uploadAndGetBlobId(new ByteArrayInputStream("notIcsFileFormat".getBytes))

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "blobIds": [ "$notParsableBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/parse",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "notParsable": [ "$notParsableBlobId" ]
           |    }, "c1"
           |]""".stripMargin)
  }

  @Test
  def parseShouldSucceedWhenMixSeveralCases(): Unit = {
    val notParsableBlobId: String = uploadAndGetBlobId(new ByteArrayInputStream("notIcsFileFormat".getBytes))
    val notFoundBlobId: String = randomBlobId
    val blobId: String = uploadAndGetBlobId(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"))
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "blobIds": [ "$notParsableBlobId", "$blobId", "$notFoundBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/parse",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "notParsable": [ "$notParsableBlobId" ],
           |        "notFound": [ "$notFoundBlobId" ],
           |        "parsed": {
           |            "$blobId": {
           |                "uid": "ea127690-0440-404b-af98-9823c855a283",
           |                "title": "Sprint planning #23",
           |                "description": "description 123",
           |                "start": "2017-01-11T16:00:00",
           |                "end": "2017-01-11T17:30:00",
           |                "utcStart": "2017-01-11T09:00:00Z",
           |                "utcEnd": "2017-01-11T10:30:00Z",
           |                "timeZone": "Asia/Ho_Chi_Minh",
           |                "duration": "PT1H30M",
           |                "location": "Hangout",
           |                "method": "REQUEST",
           |                "sequence": 0,
           |                "priority": 5,
           |                "freeBusyStatus": "busy",
           |                "privacy": "public",
           |                "organizer": {
           |                    "name": "Raphael OUAZANA",
           |                    "mailto": "ouazana@domain.tld"
           |                },
           |                "participants": [
           |                    {
           |                        "name": "Matthieu EXT_BAECHLER",
           |                        "mailto": "baechler@domain.tld",
           |                        "kind": "INDIVIDUAL",
           |                        "role": "ADMIN",
           |                        "participationStatus": "NEEDS-ACTION",
           |                        "expectReply": true
           |                    },
           |                    {
           |                        "name": "Laura ROYET",
           |                        "mailto": "royet@domain.tld",
           |                        "kind": "INDIVIDUAL",
           |                        "participationStatus": "NEEDS-ACTION",
           |                        "expectReply": true
           |                    }
           |                ],
           |                "extensionFields": {
           |                    "X-OPENPAAS-VIDEOCONFERENCE": ["https://jitsi.linagora.com/abcd"]
           |                },
           |                "recurrenceRules":
           |                  [{   "frequency": "yearly",
           |                       "byDay": [ "mo" ],
           |                       "byMonth": [ "10" ],
           |                       "bySetPosition": [ 1, 2 ],
           |                       "until":"2024-01-11T09:00:00Z"
           |                }]
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def parseShouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "blobIds": [ "123" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |      "type": "unknownMethod",
           |      "description": "Missing capability(ies): com:linagora:params:calendar:event"
           |    },
           |    "c1"]""".stripMargin)
  }

  @Test
  def parseShouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val request: String =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "blobIds": [ "123" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |      "type": "unknownMethod",
           |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:calendar:event"
           |    },
           |    "c1"]""".stripMargin)
  }

  @Test
  def parseShouldFailWhenWrongAccountId(): Unit = {
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "unknownAccountId",
         |      "blobIds": [ "0f9f65ab-dc7b-4146-850f-6e4881093965" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "accountNotFound"
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def parseShouldNotParseBlobEntryWhenDoesNotHavePermission(server: GuiceJamesServer): Unit = {
    val blobId: String = uploadAndGetBlobId(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"))

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`(baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build)
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/parse",
           |    {
           |        "accountId": "$ANDRE_ACCOUNT_ID",
           |        "notFound": [ "$blobId" ]
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def parseShouldSucceedWhenDelegated(server: GuiceJamesServer): Unit = {
    val blobId: String = uploadAndGetBlobId(ClassLoader.getSystemResourceAsStream("ics/meeting.ics"))
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(BOB, ANDRE)

    val bobAccountId = ACCOUNT_ID
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`(baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build)
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/parse",
           |    {
           |        "accountId": "$bobAccountId",
           |            "parsed": {
           |                "$blobId": {
           |                    "uid": "ea127690-0440-404b-af98-9823c855a283",
           |                    "title": "Sprint planning #23",
           |                    "description": "description 123",
           |                    "start": "2017-01-11T16:00:00",
           |                    "end": "2017-01-11T17:30:00",
           |                    "utcStart": "2017-01-11T09:00:00Z",
           |                    "utcEnd": "2017-01-11T10:30:00Z",
           |                    "timeZone": "Asia/Ho_Chi_Minh",
           |                    "duration": "PT1H30M",
           |                    "location": "Hangout",
           |                    "method": "REQUEST",
           |                    "sequence": 0,
           |                    "priority": 5,
           |                    "freeBusyStatus": "busy",
           |                    "privacy": "public",
           |                    "organizer": {
           |                        "name": "Raphael OUAZANA",
           |                        "mailto": "ouazana@domain.tld"
           |                    },
           |                    "participants": [
           |                        {
           |                            "name": "Matthieu EXT_BAECHLER",
           |                            "mailto": "baechler@domain.tld",
           |                            "kind": "INDIVIDUAL",
           |                            "role": "ADMIN",
           |                           "participationStatus": "NEEDS-ACTION",
           |                            "expectReply": true
           |                        },
           |                        {
           |                            "name": "Laura ROYET",
           |                            "mailto": "royet@domain.tld",
           |                            "kind": "INDIVIDUAL",
           |                            "participationStatus": "NEEDS-ACTION",
           |                            "expectReply": true
           |                        }
           |                    ],
           |                    "extensionFields": {
           |                        "X-OPENPAAS-VIDEOCONFERENCE": ["https://jitsi.linagora.com/abcd"]
           |                    },
           |                    "recurrenceRules":
           |                     [{    "frequency": "yearly",
           |                           "byDay": [ "mo" ],
           |                           "byMonth": [ "10" ],
           |                           "bySetPosition": [ 1, 2 ],
           |                           "until":"2024-01-11T09:00:00Z"
           |                     }]
           |                }
           |            }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def parseShouldSucceedWhenShared(server: GuiceJamesServer): Unit = {
    // Bob share rights mailbox to Andre
    val bobMailboxPath: MailboxPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobMailboxPath)
    server.getProbe(classOf[ACLProbeImpl])
      .replaceRights(bobMailboxPath, ANDRE.asString(), new MailboxACL.Rfc4314Rights(Right.Read, Right.Lookup))

    val messageHasIcsAttachment: Message = Message.Builder.of()
      .setBody(MultipartBuilder.create("mixed")
        .addBodyPart(BodyPartBuilder.create
          .setBody(ClassLoaderUtils.getSystemResourceAsByteArray("ics/meeting.ics"),"text/calendar" )
          .setContentDisposition("attachment"))
        .addBodyPart(BodyPartBuilder.create()
          .setBody("text content", "plain", StandardCharsets.UTF_8))
        .addBodyPart(BodyPartBuilder.create
          .setBody("<b>html</b> content", "html", StandardCharsets.UTF_8))
        .build)
      .build

    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, bobMailboxPath, AppendCommand.from(messageHasIcsAttachment))
      .getMessageId

    val icsBlobId: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Email/get",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "ids": ["${messageId.serialize}"]
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .jsonPath()
      .get("methodResponses[0][1].list[0].attachments[0].blobId")

    val responseOfAndreRequest: String = `given`(baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:calendar:event"],
           |  "methodCalls": [[
           |    "CalendarEvent/parse",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "blobIds": [ "$icsBlobId" ]
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

    assertThatJson(responseOfAndreRequest)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/parse",
           |    {
           |        "accountId": "$ANDRE_ACCOUNT_ID",
           |        "parsed": {
           |            "$icsBlobId": {
           |                "uid": "ea127690-0440-404b-af98-9823c855a283",
           |                "title": "Sprint planning #23",
           |                "description": "description 123",
           |                "start": "2017-01-11T16:00:00",
           |                "end": "2017-01-11T17:30:00",
           |                "utcStart": "2017-01-11T09:00:00Z",
           |                "utcEnd": "2017-01-11T10:30:00Z",
           |                "timeZone": "Asia/Ho_Chi_Minh",
           |                "duration": "PT1H30M",
           |                "location": "Hangout",
           |                "method": "REQUEST",
           |                "sequence": 0,
           |                "priority": 5,
           |                "freeBusyStatus": "busy",
           |                "privacy": "public",
           |                "organizer": {
           |                    "name": "Raphael OUAZANA",
           |                    "mailto": "ouazana@domain.tld"
           |                },
           |                "participants": [
           |                    {
           |                        "name": "Matthieu EXT_BAECHLER",
           |                        "mailto": "baechler@domain.tld",
           |                        "kind": "INDIVIDUAL",
           |                        "role": "ADMIN",
           |                        "participationStatus": "NEEDS-ACTION",
           |                        "expectReply": true
           |                    },
           |                    {
           |                        "name": "Laura ROYET",
           |                        "mailto": "royet@domain.tld",
           |                        "kind": "INDIVIDUAL",
           |                        "participationStatus": "NEEDS-ACTION",
           |                        "expectReply": true
           |                    }
           |                ],
           |                "extensionFields": {
           |                    "X-OPENPAAS-VIDEOCONFERENCE": ["https://jitsi.linagora.com/abcd"]
           |                },
           |             "recurrenceRules":
           |                  [{   "frequency": "yearly",
           |                       "byDay": [ "mo" ],
           |                       "byMonth": [ "10" ],
           |                       "bySetPosition": [ 1, 2 ],
           |                       "until":"2024-01-11T09:00:00Z"
           |                  }]
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def parseShouldFailWhenNumberOfBlobIdsTooLarge(): Unit = {
    val blobIds: Array[String] = Range.inclusive(1, 999)
      .map(_ + "")
      .toArray
    val blobIdsJson: String = Json.stringify(Json.arr(blobIds)).replace("[[", "[").replace("]]", "]")
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "blobIds":  ${blobIdsJson}
         |    },
         |    "c1"]]
         |}""".stripMargin

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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "requestTooLarge",
           |        "description": "The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def parseShouldSuccessWhenIcsMissingSomeOptionalProperty(): Unit = {
    val blobId: String = uploadAndGetBlobId(ClassLoader.getSystemResourceAsStream("ics/meeting_minimize.ics"))

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/parse",
         |    {
         |      "accountId": "$ACCOUNT_ID",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/parse",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "parsed": {
           |            "$blobId": {
           |                "uid": "037aaad3-17c9-47c8-bd6b-f2cbfe925ef7",
           |                "title": "MOB: integration tests",
           |                "start": "2023-03-09T14:00:00",
           |                "end": "2023-03-09T14:00:00",
           |                "utcStart": "2023-03-09T07:00:00Z",
           |                "utcEnd": "2023-03-09T07:00:00Z",
           |                "timeZone": "Asia/Ho_Chi_Minh",
           |                "method": "REQUEST",
           |                "sequence": 0,
           |                "freeBusyStatus": "busy",
           |                "privacy": "secret",
           |                "organizer": {
           |                    "name": "Benoît TELLIER",
           |                    "mailto": "btellier@domain.tld"
           |                },
           |                "participants": [
           |                    {
           |                        "name": "Van Tung TRAN",
           |                        "mailto": "vttran@domain.tld",
           |                        "kind": "INDIVIDUAL",
           |                        "role": "REQ-PARTICIPANT",
           |                        "participationStatus": "NEEDS-ACTION",
           |                        "expectReply": true
           |                    }
           |                ],
           |                "extensionFields": {
           |                    "X-OPENPAAS-VIDEOCONFERENCE": [""]
           |                },
           |             "recurrenceRules": []
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  private def uploadAndGetBlobId(payload: InputStream): String =
    `given`
      .basePath("")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
      .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .get("blobId")
}
