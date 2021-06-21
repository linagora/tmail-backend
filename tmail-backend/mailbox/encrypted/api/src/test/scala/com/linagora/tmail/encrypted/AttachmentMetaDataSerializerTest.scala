package com.linagora.tmail.encrypted

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsValue, Json}

class AttachmentMetaDataSerializerTest {

  @Test
  def serializeShouldBeSuccess(): Unit = {
    val attachmentMetadata: AttachmentMetadata = AttachmentMetadata(
      position = 1,
      blobId = "encryptedAttachment_123_3",
      name = Some("name1"),
      contentType = "Content2",
      cid = Some("cid2"),
      isLine = true,
      size = 9999)

    val actualValue: JsValue = AttachmentMetaDataSerializer.serialize(attachmentMetadata)
    val expectedValue: JsValue = Json.parse(
      """
        |{
        |  "position" : 1,
        |  "blobId" : "encryptedAttachment_123_3",
        |  "name" : "name1",
        |  "contentType" : "Content2",
        |  "cid" : "cid2",
        |  "isLine" : true,
        |  "size" : 9999
        |}""".stripMargin)

    assertThat(actualValue).isEqualTo(expectedValue)
  }

  @Test
  def serializeListShouldBeSuccess(): Unit = {
    val attachmentMetadata1: AttachmentMetadata = AttachmentMetadata(
      position = 1,
      blobId = "encryptedAttachment_123_3",
      name = Some("name1"),
      contentType = "Content2",
      cid = Some("cid2"),
      isLine = true,
      size = 9999)
    val attachmentMetadata2: AttachmentMetadata = AttachmentMetadata(
      position = 2,
      blobId = "encryptedAttachment_1234_321",
      name = Some("name12151"),
      contentType = "Content232gag",
      cid = Some("cid234562"),
      isLine = false,
      size = 123459)

    val actualValue: JsValue = AttachmentMetaDataSerializer.serializeList(List(attachmentMetadata1, attachmentMetadata2))
    val expectedValue: JsValue = Json.parse(
      """
        |[ {
        |  "position" : 1,
        |  "blobId" : "encryptedAttachment_123_3",
        |  "name" : "name1",
        |  "contentType" : "Content2",
        |  "cid" : "cid2",
        |  "isLine" : true,
        |  "size" : 9999
        |}, {
        |  "position" : 2,
        |  "blobId" : "encryptedAttachment_1234_321",
        |  "name" : "name12151",
        |  "contentType" : "Content232gag",
        |  "cid" : "cid234562",
        |  "isLine" : false,
        |  "size" : 123459
        |} ]""".stripMargin)

    assertThat(actualValue).isEqualTo(expectedValue)
  }
}
