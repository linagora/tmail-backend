package com.linagora.openpaas.encrypted

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets

import com.linagora.openpaas.pgp.Decrypter
import org.apache.james.jmap.JMAPTestingConstants.{ALICE, BOB}
import org.apache.james.mailbox.model.{FetchGroup, MailboxPath, MessageRange}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageManager}
import org.apache.james.mime4j.dom.{Message, Multipart}
import org.apache.james.mime4j.message.{DefaultMessageBuilder, DefaultMessageWriter}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

trait EncryptedMessageManagerContract {

  def keystoreManager: KeystoreManager
  def mailboxManager: MailboxManager

  private lazy val message: Message = Message.Builder.of
    .setSubject("test").setSender(BOB.asString)
    .setFrom(BOB.asString)
    .setTo(ALICE.asString)
    .setBody("testmail", StandardCharsets.UTF_8).build

  def path: MailboxPath
  def session: MailboxSession
  def messageManager: MessageManager
  def testee: EncryptedMessageManager

  @Test
  def commandAppendShouldEncryptMessage(): Unit = {
    val keyBytes = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes
    Mono.from(keystoreManager.save(BOB, keyBytes)).block

    testee.appendMessage(MessageManager.AppendCommand.from(message), session)
    val messages = mailboxManager.getMailbox(path, session).getMessages(MessageRange.all, FetchGroup.BODY_CONTENT, session)
    val result = messages.next

    assertThat(new String(result.getBody.getInputStream.readAllBytes, StandardCharsets.UTF_8)).doesNotContain("testmail")
  }

  @Test
  def commandAppendShouldEncryptMessageWithMultipleKeys(): Unit = {
    val keyBytes1 = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes
    val keyBytes2 = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes
    Mono.from(keystoreManager.save(BOB, keyBytes1)).block
    Mono.from(keystoreManager.save(BOB, keyBytes2)).block

    testee.appendMessage(MessageManager.AppendCommand.from(message), session)

    val messages = mailboxManager.getMailbox(path, session).getMessages(MessageRange.all, FetchGroup.BODY_CONTENT, session)
    val result = messages.next

    assertThat(new String(result.getBody.getInputStream.readAllBytes, StandardCharsets.UTF_8)).doesNotContain("testmail")
  }

  @Test
  def commandAppendedWithSingleKeyShouldBeDecryptable(): Unit = {
    val keyBytes = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes
    Mono.from(keystoreManager.save(BOB, keyBytes)).block

    testee.appendMessage(MessageManager.AppendCommand.from(message), session)

    val messages = mailboxManager.getMailbox(path, session).getMessages(MessageRange.all, FetchGroup.FULL_CONTENT, session)
    val result = messages.next

    val body = new DefaultMessageBuilder().parseMessage(result.getFullContent.getInputStream).getBody
    val encryptedMultiPart = body.asInstanceOf[Multipart]
    val encryptedBodyPart = encryptedMultiPart.getBodyParts.get(1).getBody
    val encryptedBodyBytes = new ByteArrayOutputStream
    new DefaultMessageWriter().writeBody(encryptedBodyPart, encryptedBodyBytes)
    val decryptedPayload = Decrypter.forKey(ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.private"), "123456".toCharArray).decrypt(new ByteArrayInputStream(encryptedBodyBytes.toByteArray)).readAllBytes

    assertThat(new String(decryptedPayload, StandardCharsets.UTF_8)).contains("testmail")
  }

  @Test
  def commandAppendedWithMultipleKeysShouldBeDecryptable(): Unit = {
    val keyBytes1 = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes
    val keyBytes2 = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes
    Mono.from(keystoreManager.save(BOB, keyBytes1)).block
    Mono.from(keystoreManager.save(BOB, keyBytes2)).block

    testee.appendMessage(MessageManager.AppendCommand.from(message), session)

    val messages = mailboxManager.getMailbox(path, session).getMessages(MessageRange.all, FetchGroup.FULL_CONTENT, session)
    val result = messages.next

    val body = new DefaultMessageBuilder().parseMessage(result.getFullContent.getInputStream).getBody
    val encryptedMultiPart = body.asInstanceOf[Multipart]
    val encryptedBodyPart = encryptedMultiPart.getBodyParts.get(1).getBody
    val encryptedBodyBytes = new ByteArrayOutputStream
    new DefaultMessageWriter().writeBody(encryptedBodyPart, encryptedBodyBytes)
    val decryptedPayload = Decrypter.forKey(ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.private"), "123456".toCharArray).decrypt(new ByteArrayInputStream(encryptedBodyBytes.toByteArray)).readAllBytes

    assertThat(new String(decryptedPayload, StandardCharsets.UTF_8)).contains("testmail")
  }

  @Test
  def commandAppendShouldThrowWhenNoPublicKey(): Unit = {
    testee.appendMessage(MessageManager.AppendCommand.from(message), session)
    val messages = mailboxManager.getMailbox(path, session).getMessages(MessageRange.all, FetchGroup.BODY_CONTENT, session)
    val result = messages.next

    assertThat(new String(result.getBody.getInputStream.readAllBytes, StandardCharsets.UTF_8)).contains("testmail")
  }

  @Test
  def commandAppendShouldNotEncryptWhenNoPublicKey(): Unit = {
    testee.appendMessage(MessageManager.AppendCommand.from(message), session)
    val messages = mailboxManager.getMailbox(path, session).getMessages(MessageRange.all, FetchGroup.BODY_CONTENT, session)
    val result = messages.next

    assertThat(new String(result.getBody.getInputStream.readAllBytes, StandardCharsets.UTF_8)).contains("testmail")
  }
}
