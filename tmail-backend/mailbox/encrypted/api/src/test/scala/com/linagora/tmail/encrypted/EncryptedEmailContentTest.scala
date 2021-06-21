package com.linagora.tmail.encrypted

import com.google.common.io.ByteSource
import com.linagora.tmail.pgp.{Decrypter, Encrypter}
import org.apache.james.jmap.api.model.Preview
import org.apache.james.mailbox.inmemory.InMemoryMessageId
import org.apache.james.mailbox.model.{MessageId, ParsedAttachment}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeAll, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import play.api.libs.json.Json

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.Security

object EncryptedEmailContentTest {
  val HTML_CONTENT: String = "<b>html</b> content"
  val CLEAR_EMAIL_CONTENT: ClearEmailContent = ClearEmailContent(
    preview = Preview.from("preview 123321"),
    hasAttachment = true,
    html = HTML_CONTENT,
    attachments = List(ParsedAttachment.builder
      .contentType("content")
      .content(ByteSource.wrap("payload123321".getBytes(StandardCharsets.UTF_8)))
      .noName
      .noCid
      .inline(false)))

  val MESSAGE_ID: MessageId = new InMemoryMessageId.Factory().fromString("123")

  @BeforeAll
  def setUp(): Unit = {
    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
  }
}

class EncryptedEmailContentTest {

  import EncryptedEmailContentTest._

  val encrypter: Encrypter = Encrypter.forKeys(ClassLoader.getSystemClassLoader
    .getResourceAsStream("gpg.pub")
    .readAllBytes)
  val decrypter: Decrypter = Decrypter.forKey(ClassLoader.getSystemClassLoader
    .getResourceAsStream("gpg.private"),
    "123456".toCharArray)
  val testee: EncryptedEmailContentFactory = new EncryptedEmailContentFactory(encrypter)

  @Test
  def encryptedPreviewShouldEncrypt(): Unit = {
    val encryptedEmailContent: EncryptedEmailContent = testee.encrypt(CLEAR_EMAIL_CONTENT, MESSAGE_ID)
    val decryptedPreview: String = decrypt(encryptedEmailContent.encryptedPreview)
    assertThat(CLEAR_EMAIL_CONTENT.preview)
      .isEqualTo(Preview.from(decryptedPreview))
  }

  @Test
  def encryptedHtmlShouldEncrypt(): Unit = {
    val encryptedEmailContent: EncryptedEmailContent = testee.encrypt(CLEAR_EMAIL_CONTENT, MESSAGE_ID)
    assertThat(CLEAR_EMAIL_CONTENT.html)
      .isEqualTo(decrypt(encryptedEmailContent.encryptedHtml))
  }

  @Test
  def encryptedAttachmentMetadataShouldEncrypt(): Unit = {
    val encryptedEmailContent: EncryptedEmailContent = testee.encrypt(CLEAR_EMAIL_CONTENT, MESSAGE_ID)
    val decryptedAttachmentMetadata: String = decrypt(encryptedEmailContent.encryptedAttachmentMetadata.get)

    assertThat(AttachmentMetaDataSerializer
      .serializeList(AttachmentMetadata.fromJava(CLEAR_EMAIL_CONTENT.attachments, MESSAGE_ID)))
      .isEqualTo(Json.parse(decryptedAttachmentMetadata))
  }

  @ParameterizedTest
  @ValueSource(booleans = Array(true, false))
  def hasAttachmentShouldNoEncrypt(isHasAttachment: Boolean): Unit = {
    val clearEmailContent: ClearEmailContent = ClearEmailContent(
      preview = Preview.from("preview 123321"),
      hasAttachment = isHasAttachment,
      html = HTML_CONTENT,
      attachments = List(ParsedAttachment.builder
        .contentType("content")
        .content(ByteSource.wrap("payload123321".getBytes(StandardCharsets.UTF_8)))
        .noName
        .noCid
        .inline(false)))
    val encryptedEmailContent: EncryptedEmailContent = testee.encrypt(clearEmailContent, MESSAGE_ID)
    assertThat(clearEmailContent.hasAttachment)
      .isEqualTo(encryptedEmailContent.hasAttachment)
  }

  @Test
  def encryptedAttachmentContentsShouldEncrypt(): Unit = {
    val encryptedEmailContent: EncryptedEmailContent = testee.encrypt(CLEAR_EMAIL_CONTENT, MESSAGE_ID)
    val decryptedAttachmentContents: List[String] = encryptedEmailContent.encryptedAttachmentContents
      .map(encrypted => decrypt(encrypted))
    val clearAttachmentContents: List[String] = CLEAR_EMAIL_CONTENT.attachments
      .map(clearAttachment => new String(clearAttachment.getContent.read(), StandardCharsets.UTF_8))
    assertThat(decryptedAttachmentContents)
      .hasSameClassAs(clearAttachmentContents)
  }

  @Test
  def encryptedAttachmentMetadataShouldReturnNoneWhenAttachmentsAreEmpty(): Unit = {
    val clearEmailContent: ClearEmailContent = ClearEmailContent(
      preview = Preview.EMPTY,
      hasAttachment = true,
      html = "html",
      attachments = List())
    val encryptedEmailContent: EncryptedEmailContent = testee.encrypt(clearEmailContent, MESSAGE_ID)
    assertThat(encryptedEmailContent.encryptedAttachmentMetadata)
      .isEqualTo(None)
  }

  @Test
  def encryptedAttachmentContentsShouldReturnEmptyWhenAttachmentsAreEmpty(): Unit = {
    val clearEmailContent: ClearEmailContent = ClearEmailContent(
      preview = Preview.EMPTY,
      hasAttachment = true,
      html = "html",
      attachments = List())
    val encryptedEmailContent: EncryptedEmailContent = testee.encrypt(clearEmailContent, MESSAGE_ID)
    assertThat(encryptedEmailContent.encryptedAttachmentContents.isEmpty)
      .isTrue
  }

  @Test
  def encryptedPreviewShouldEncryptEvenPreviewIsEmpty(): Unit = {
    val clearEmailContent: ClearEmailContent = ClearEmailContent(
      preview = Preview.EMPTY,
      hasAttachment = true,
      html = "html",
      attachments = List())
    val encryptedEmailContent: EncryptedEmailContent = testee.encrypt(clearEmailContent, MESSAGE_ID)
    val decryptedPreview: String = decrypt(encryptedEmailContent.encryptedPreview)
    assertThat(Preview.from(decryptedPreview))
      .isEqualTo(Preview.EMPTY)
  }

  @Test
  def encryptedHtmlShouldEncryptEvenHtmlIsEmpty(): Unit = {
    val clearEmailContent: ClearEmailContent = ClearEmailContent(
      preview = Preview.EMPTY,
      hasAttachment = true,
      html = "",
      attachments = List())
    val encryptedEmailContent: EncryptedEmailContent = testee.encrypt(clearEmailContent, MESSAGE_ID)
    assertThat(decrypt(encryptedEmailContent.encryptedHtml))
      .isEmpty()
  }

  def decrypt(encryptedPayload: String): String = {
    val decryptedPayload: Array[Byte] = decrypter.decrypt(new ByteArrayInputStream(encryptedPayload.getBytes))
      .readAllBytes()
    new String(decryptedPayload, StandardCharsets.UTF_8)
  }
}
