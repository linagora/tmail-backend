package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.encrypted.{EncryptedEmailFastView, EncryptedPreview}
import com.linagora.tmail.james.jmap.json.EncryptedEmailSerializerTest.{ACCOUNT_ID, MESSAGE_ID_FACTORY}
import com.linagora.tmail.james.jmap.model.{EncryptedEmailGetRequest, EncryptedEmailGetResponse}
import org.apache.james.jmap.core.{AccountId, Id, UuidState}
import org.apache.james.jmap.mail.{Email, EmailIds, EmailNotFound, UnparsedEmailId}
import org.apache.james.mailbox.inmemory.InMemoryMessageId
import org.apache.james.mailbox.model.MessageId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsValue, Json}

object EncryptedEmailSerializerTest {
  private val ACCOUNT_ID: AccountId = AccountId(Id.validate("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8").toOption.get)
  private val MESSAGE_ID_FACTORY: MessageId.Factory = new InMemoryMessageId.Factory()
}

class EncryptedEmailSerializerTest {

  @Test
  def serializeEncryptedEmailGetResponseShouldSuccess(): Unit = {
    val messageId: MessageId = MESSAGE_ID_FACTORY.generate()
    val notFoundMessageId: MessageId = MESSAGE_ID_FACTORY.generate()
    val EncryptedEmailFastView: EncryptedEmailFastView = new EncryptedEmailFastView(EncryptedPreview("encryptedPreview1"), true)
    val encryptedEmailGetResponse: EncryptedEmailGetResponse = EncryptedEmailGetResponse(
      accountId = ACCOUNT_ID,
      state = UuidState.parse("6e0dd59d-660e-4d9b-b22f-0354479f47b4").toOption.get,
      list = Some(Map(messageId -> EncryptedEmailFastView)),
      notFound = Some(EmailNotFound(Set(Email.asUnparsed(notFoundMessageId).get))))

    val actualValue: JsValue = EncryptedEmailSerializer.serializeEncryptedEmailGetResponse(encryptedEmailGetResponse)

    val expectedValue: JsValue = Json.parse(
      s"""
        |{
        |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
        |    "state": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
        |    "list": [
        |        [
        |            "${messageId.serialize()}",
        |            {
        |                "encryptedPreview": "encryptedPreview1",
        |                "hasAttachment": true
        |            }
        |        ]
        |    ],
        |    "notFound": [
        |        "${notFoundMessageId.serialize()}"
        |    ]
        |}""".stripMargin)

    assertThat(actualValue)
      .isEqualTo(expectedValue)
  }

  @Test
  def deserializeEncryptedEmailGetRequestShouldSuccess(): Unit = {
    val unparsedEmailId1: UnparsedEmailId = Email.asUnparsed(MESSAGE_ID_FACTORY.generate()).get
    val unparsedEmailId2: UnparsedEmailId = Email.asUnparsed(MESSAGE_ID_FACTORY.generate()).get

    val expectedEncryptedEmailGetRequest: EncryptedEmailGetRequest = EncryptedEmailGetRequest(
      accountId = ACCOUNT_ID,
      ids = EmailIds(List(unparsedEmailId1, unparsedEmailId2)))

    val actualEncryptedEmailGetRequest: EncryptedEmailGetRequest = EncryptedEmailSerializer.deserializeEncryptedEmailGetRequest(
      Json.parse(
        s"""{
           |    "accountId": "${ACCOUNT_ID.id.value}",
           |    "ids": ["${unparsedEmailId1.id.value}", "${unparsedEmailId2.id.value}"]
           |  }""".stripMargin))
      .get

    assertThat(actualEncryptedEmailGetRequest)
      .isEqualTo(expectedEncryptedEmailGetRequest)
  }
}
