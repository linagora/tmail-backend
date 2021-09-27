package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.encrypted.{EncryptedAttachmentMetadata, EncryptedEmailDetailedView, EncryptedEmailFastView, EncryptedHtml, EncryptedPreview}
import com.linagora.tmail.james.jmap.json.EncryptedEmailSerializerTest.{ACCOUNT_ID, MESSAGE_ID_FACTORY}
import com.linagora.tmail.james.jmap.model.{EncryptedEmailDetailedResponse, EncryptedEmailGetRequest, EncryptedEmailGetResponse}
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
    val EncryptedEmailFastView: EncryptedEmailFastView = new EncryptedEmailFastView(messageId, EncryptedPreview("encryptedPreview1"),
      true)
    val encryptedEmailGetResponse: EncryptedEmailGetResponse = EncryptedEmailGetResponse(
      accountId = ACCOUNT_ID,
      state = UuidState.parse("6e0dd59d-660e-4d9b-b22f-0354479f47b4").toOption.get,
      list = List(EncryptedEmailFastView),
      notFound = EmailNotFound(Set(Email.asUnparsed(notFoundMessageId).get)))

    val actualValue: JsValue = EncryptedEmailSerializer.serializeEncryptedEmailGetResponse(encryptedEmailGetResponse)

    val expectedValue: JsValue = Json.parse(
      s"""
        |{
        |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
        |    "state": "6e0dd59d-660e-4d9b-b22f-0354479f47b4",
        |    "list": [
        |            {
        |                "id": "${messageId.serialize()}",
        |                "encryptedPreview": "encryptedPreview1",
        |                "hasAttachment": true
        |            }
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

  @Test
  def serializeEncryptedEmailDetailedResponseShouldSuccess(): Unit = {
    val messageId: MessageId = MESSAGE_ID_FACTORY.generate()
    val notFoundMessageId: MessageId = MESSAGE_ID_FACTORY.generate()
    val encryptedEmailDetailedView: EncryptedEmailDetailedView = EncryptedEmailDetailedView(
      id = messageId,
      encryptedPreview = EncryptedPreview("EncryptedPreview1"),
      encryptedHtml = EncryptedHtml("EncryptedHtml1"),
      hasAttachment = true,
      encryptedAttachmentMetadata = Some(EncryptedAttachmentMetadata("EncryptedAttachmentMetadata1")))
    val encryptedEmailDetailedResponse:EncryptedEmailDetailedResponse = EncryptedEmailDetailedResponse(
      accountId = ACCOUNT_ID,
      state = UuidState.INSTANCE,
      list = List(encryptedEmailDetailedView),
      notFound = EmailNotFound(Set(Email.asUnparsed(notFoundMessageId).get)))

    val actualValue: JsValue = EncryptedEmailSerializer.serializeEncryptedEmailDetailedResponse(encryptedEmailDetailedResponse)

    val expectedValue: JsValue = Json.parse(
      s"""
        |{
        |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
        |    "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
        |    "list": [
        |            {
        |                "id": "${messageId.serialize()}",
        |                "encryptedPreview": "EncryptedPreview1",
        |                "encryptedHtml": "EncryptedHtml1",
        |                "hasAttachment": true,
        |                "encryptedAttachmentMetadata": "EncryptedAttachmentMetadata1"
        |            }
        |    ],
        |    "notFound": [
        |        "${notFoundMessageId.serialize()}"
        |    ]
        |}""".stripMargin)

    assertThat(actualValue)
      .isEqualTo(expectedValue)
  }
}
