package com.linagora.tmail.james.jmap.json

import java.time.ZonedDateTime

import com.linagora.tmail.james.jmap.model.{EmailRecoveryAction, EmailRecoveryActionCreationId, EmailRecoveryActionCreationRequest, EmailRecoveryActionCreationResponse, EmailRecoveryActionGetRequest, EmailRecoveryActionGetResponse, EmailRecoveryActionSetRequest, EmailRecoveryActionSetResponse, EmailRecoveryDeletedAfter, EmailRecoveryDeletedBefore, EmailRecoveryHasAttachment, EmailRecoveryReceivedAfter, EmailRecoveryReceivedBefore, EmailRecoveryRecipient, EmailRecoverySender, EmailRecoverySubject, ErrorRestoreCount, SuccessfulRestoreCount, UnparsedEmailRecoveryActionId}
import eu.timepit.refined.auto._
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Id, Properties, SetError, UTCDate}
import org.apache.james.task.TaskId
import org.apache.james.task.TaskManager.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json}

import scala.collection.Seq

class EmailRecoveryActionSerializerTest {

  @Test
  def deserializeActionCreationRequestShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "deletedBefore": "2016-06-09T01:07:06Z",
        |    "deletedAfter": "2017-07-09T01:07:06Z",
        |    "receivedBefore": "2018-08-09T01:07:06Z",
        |    "receivedAfter": "2019-09-09T01:07:06Z",
        |    "hasAttachment": true,
        |    "subject": "Simple topic",
        |    "sender": "bob@domain.tld",
        |    "recipients": [
        |        "alice@domain.tld",
        |        "andre@example.tld"
        |    ]
        |}""".stripMargin)

    val deserializeResult: JsResult[EmailRecoveryActionCreationRequest] = EmailRecoveryActionSerializer.deserializeSetCreationRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get)
      .isEqualTo(EmailRecoveryActionCreationRequest(
        deletedBefore = Some(EmailRecoveryDeletedBefore(UTCDate(ZonedDateTime.parse("2016-06-09T01:07:06Z")))),
        deletedAfter = Some(EmailRecoveryDeletedAfter(UTCDate(ZonedDateTime.parse("2017-07-09T01:07:06Z")))),
        receivedBefore = Some(EmailRecoveryReceivedBefore(UTCDate(ZonedDateTime.parse("2018-08-09T01:07:06Z")))),
        receivedAfter = Some(EmailRecoveryReceivedAfter(UTCDate(ZonedDateTime.parse("2019-09-09T01:07:06Z")))),
        hasAttachment = Some(EmailRecoveryHasAttachment(true)),
        subject = Some(EmailRecoverySubject("Simple topic")),
        sender = Some(EmailRecoverySender(new MailAddress("bob@domain.tld"))),
        recipients = Some(Seq(EmailRecoveryRecipient(new MailAddress("alice@domain.tld")),
          EmailRecoveryRecipient(new MailAddress("andre@example.tld"))))))
  }

  @Test
  def deserializeSetRequestShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "create": {
        |        "clientId1": {
        |            "deletedBefore": "2016-06-09T01:07:06Z",
        |            "deletedAfter": "2017-07-09T01:07:06Z",
        |            "receivedBefore": "2018-08-09T01:07:06Z",
        |            "receivedAfter": "2019-09-09T01:07:06Z",
        |            "hasAttachment": true,
        |            "subject": "Simple topic",
        |            "sender": "bob@domain.tld",
        |            "recipients": [
        |                "alice@domain.tld",
        |                "andre@example.tld"
        |            ]
        |        }
        |    }
        |}""".stripMargin)

    val deserializeResult: JsResult[EmailRecoveryActionSetRequest] = EmailRecoveryActionSerializer.deserializeSetRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get.create.get.head._1)
      .isEqualTo(EmailRecoveryActionCreationId("clientId1"))
  }


  @Test
  def serializeSetResponseShouldSuccess(): Unit = {
    val notCreated: Map[EmailRecoveryActionCreationId, SetError] = Map((EmailRecoveryActionCreationId("K39"),
      SetError(SetError.invalidArgumentValue, SetErrorDescription("des1"), None)))

    val created: Map[EmailRecoveryActionCreationId, EmailRecoveryActionCreationResponse] =
      Map(EmailRecoveryActionCreationId("K38") -> EmailRecoveryActionCreationResponse(TaskId.fromString("4bf6d081-aa30-11e9-bf6c-2d3b9e84aafd")))


    val response: EmailRecoveryActionSetResponse = EmailRecoveryActionSetResponse(created = Some(created), notCreated = Some(notCreated))
    assertThat(EmailRecoveryActionSerializer.serializeSetResponse(response))
      .isEqualTo(Json.parse(
        """{
          |    "created": {
          |        "K38": {
          |            "id": "4bf6d081-aa30-11e9-bf6c-2d3b9e84aafd"
          |        }
          |    },
          |    "notCreated": {
          |        "K39": {
          |            "type": "invalidArguments",
          |            "description": "des1"
          |        }
          |    }
          |}""".stripMargin))
  }

  @Test
  def deserializeGetRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |		"ids": ["2034-495-05857-57abcd-0876664"],
        |   "properties": ["status"]
        |	}""".stripMargin)

    val deserializeResult: JsResult[EmailRecoveryActionGetRequest] = EmailRecoveryActionSerializer.deserializeGetRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get.ids.list.head)
      .isEqualTo(UnparsedEmailRecoveryActionId("2034-495-05857-57abcd-0876664"))
    assertThat(deserializeResult.get.properties.get.contains("status"))
      .isTrue
  }

  @Test
  def serializeGetResponseShouldSucceed(): Unit = {
    val list: Seq[EmailRecoveryAction] = Seq(EmailRecoveryAction(id = TaskId.fromString("77731634-ea82-4a1a-bd4c-9f8ece4f66c7"),
      successfulRestoreCount = SuccessfulRestoreCount(99L),
      errorRestoreCount = ErrorRestoreCount(1L),
      status = Status.IN_PROGRESS))
    val notFound: Set[UnparsedEmailRecoveryActionId] = Set(UnparsedEmailRecoveryActionId(Id.validate("whatever").toOption.get))
    val response: EmailRecoveryActionGetResponse = EmailRecoveryActionGetResponse(list, notFound)

    assertThat(EmailRecoveryActionSerializer.serializeGetResponse(response, EmailRecoveryActionGetRequest.allSupportedProperties))
      .isEqualTo(Json.parse(
        """{
          |    		"list": [{
          |    			"id": "77731634-ea82-4a1a-bd4c-9f8ece4f66c7",
          |    			"successfulRestoreCount": 99,
          |    			"errorRestoreCount": 1,
          |    			"status": "inProgress"
          |    		}],
          |    		"notFound": ["whatever"]
          |    	}""".stripMargin))
  }

  @Test
  def serializeGetResponseShouldFilterByProperties(): Unit = {
    val list: Seq[EmailRecoveryAction] = Seq(EmailRecoveryAction(id = TaskId.fromString("77731634-ea82-4a1a-bd4c-9f8ece4f66c7"),
      successfulRestoreCount = SuccessfulRestoreCount(99L),
      errorRestoreCount = ErrorRestoreCount(1L),
      status = Status.IN_PROGRESS))
    val notFound: Set[UnparsedEmailRecoveryActionId] = Set(UnparsedEmailRecoveryActionId(Id.validate("whatever").toOption.get))
    val response: EmailRecoveryActionGetResponse = EmailRecoveryActionGetResponse(list, notFound)

    assertThat(EmailRecoveryActionSerializer.serializeGetResponse(response, Properties("id", "status")))
      .isEqualTo(Json.parse(
        """{
          |    		"list": [{
          |    			"id": "77731634-ea82-4a1a-bd4c-9f8ece4f66c7",
          |    			"status": "inProgress"
          |    		}],
          |    		"notFound": ["whatever"]
          |    	}""".stripMargin))
  }

}
