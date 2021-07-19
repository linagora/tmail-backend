package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.json.Fixture.{ACCOUNT_ID, MESSAGE_ID_FACTORY, STATE}
import com.linagora.tmail.james.jmap.model.{EmailSendCreationId, EmailSendCreationResponse, EmailSendResponse}
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.mail.{BlobId, Email, EmailSubmissionId, ThreadId}
import org.apache.james.mailbox.model.MessageId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsValue, Json}

class EmailSendSerializerTest {

  @Test
  def deserializeEmailSendCreationCreateShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """
        |{
        |    "email/create": {
        |        "mailboxIds": {
        |            "123": true
        |        },
        |        "to": [
        |            {
        |                "email": "email@domain.tld"
        |            }
        |        ],
        |        "from": [
        |            {
        |                "email": "from@domain.tld"
        |            }
        |        ]
        |    },
        |    "emailSubmission/set": {
        |        "envelope": {
        |            "mailFrom": {
        |                "email": "email3@domain.tld"
        |            },
        |            "rcptTo": [
        |                {
        |                    "email": "email4@domain.tld"
        |                }
        |            ]
        |        }
        |    }
        |}""".stripMargin)

    assertThat(EmailSendSerializer.deserializeEmailSendCreationRequest(jsInput).isSuccess)
      .isEqualTo(true)
  }

  @Test
  def deserializeEmailSendRequestShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      s"""
         |{
         |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
         |    "create": {
         |        "K87": {
         |            "email/create": {
         |                "mailboxIds": {
         |                    "123": true
         |                },
         |                "to": [
         |                    {
         |                        "email": "email@domain.tld"
         |                    }
         |                ],
         |                "from": [
         |                    {
         |                        "email": "from@domain.tld"
         |                    }
         |                ]
         |            },
         |            "emailSubmission/set": {
         |                "envelope": {
         |                    "mailFrom": {
         |                        "email": "email3@domain.tld"
         |                    },
         |                    "rcptTo": [
         |                        {
         |                            "email": "email4@domain.tld"
         |                        }
         |                    ]
         |                }
         |            }
         |        }
         |    },
         |    "onSuccessUpdateEmail": {
         |        "#K87": {
         |            "keywords": {
         |                "$$sent": true
         |            }
         |        }
         |    },
         |    "onSuccessDestroyEmail": [
         |        "#K87"
         |    ]
         |}""".stripMargin)

    assertThat(EmailSendSerializer.deserializeEmailSendRequest(jsInput).isSuccess)
      .isEqualTo(true)
  }

  @Test
  def serializeEmailSendResponseShouldSuccess(): Unit = {
    val messageId1: MessageId = MESSAGE_ID_FACTORY.generate()
    val messageId2: MessageId = MESSAGE_ID_FACTORY.generate()
    val created: Map[EmailSendCreationId, EmailSendCreationResponse] = Map(
      (EmailSendCreationId("isci1"),
        EmailSendCreationResponse(
          emailSubmissionId = EmailSubmissionId("emailSubmissionId1"),
          emailId = messageId1,
          blobId = Some(BlobId("blob1")),
          threadId = ThreadId("thread1"),
          size = Email.sanitizeSize(11))),
      (EmailSendCreationId("isci2"),
        EmailSendCreationResponse(
          emailSubmissionId = EmailSubmissionId("emailSubmissionId2"),
          emailId = messageId2,
          blobId = None,
          threadId = ThreadId("thread2"),
          size = Email.sanitizeSize(22))))

    val notCreated: Map[EmailSendCreationId, SetError] = Map(
      (EmailSendCreationId("isci3"),
        SetError(SetError.invalidArgumentValue, SetErrorDescription("des1"), None)),
      (EmailSendCreationId("isci4"),
        SetError(SetError.forbiddenFromValue, SetErrorDescription("des2"), Some(Properties("p1")))))

    val emailSendResponse: EmailSendResponse = EmailSendResponse(
      accountId = ACCOUNT_ID,
      newState = STATE,
      created = Some(created),
      notCreated = Some(notCreated))
    val actualValue: JsValue = EmailSendSerializer.serializeEmailSendResponse(emailSendResponse)

    val expectedValue: JsValue = Json.parse(
      s"""{
         |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
         |    "newState": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
         |    "created": {
         |        "isci1": {
         |            "emailSubmissionId": "emailSubmissionId1",
         |            "emailId": "${messageId1.serialize()}",
         |            "blobId": "blob1",
         |            "threadId": "thread1",
         |            "size": 11
         |        },
         |        "isci2": {
         |            "emailSubmissionId": "emailSubmissionId2",
         |            "emailId": "${messageId2.serialize()}",
         |            "threadId": "thread2",
         |            "size": 22
         |        }
         |    },
         |    "notCreated": {
         |        "isci3": {
         |            "type": "invalidArguments",
         |            "description": "des1"
         |        },
         |        "isci4": {
         |            "type": "forbiddenFrom",
         |            "description": "des2",
         |            "properties": [
         |                "p1"
         |            ]
         |        }
         |    }
         |}""".stripMargin)
    assertThat(actualValue)
      .isEqualTo(expectedValue)
  }
}
