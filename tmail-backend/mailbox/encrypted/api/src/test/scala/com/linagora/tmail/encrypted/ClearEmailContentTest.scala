package com.linagora.tmail.encrypted

import com.google.common.io.ByteSource
import org.apache.james.jmap.api.model.Preview
import org.apache.james.jmap.draft.utils.JsoupHtmlTextExtractor
import org.apache.james.mailbox.store.mail.model.impl.MessageParser
import org.apache.james.mime4j.dom.{Message, Multipart}
import org.apache.james.mime4j.message.{BasicBodyFactory, BodyPartBuilder, MultipartBuilder}
import org.apache.james.util.html.HtmlTextExtractor
import org.apache.james.util.mime.MessageContentExtractor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.{BeforeEach, Test}

import java.nio.charset.StandardCharsets
import java.util.stream.IntStream
import scala.util.Try

object ClearEmailContentTest {
  val TEXT_CONTENT: String = "text content"
  val HTML_CONTENT: String = "<b>html</b> content"
  val BINARY_CONTENT: String = "binary"
  val ATTACHMENT_CONTENT: String = "attachment content"
}

class ClearEmailContentTest {

  import ClearEmailContentTest._

  var previewFactory: Preview.Factory = _
  var htmlPart: BodyPartBuilder = _
  var textPart: BodyPartBuilder = _
  var textAttachment: BodyPartBuilder = _

  var clearEmailContentFactory : ClearEmailContentFactory = _

  @BeforeEach
  def setUp(): Unit = {
    val messageContentExtractor: MessageContentExtractor = new MessageContentExtractor()
    val htmlTextExtractor: HtmlTextExtractor = new JsoupHtmlTextExtractor
    previewFactory = new Preview.Factory(new MessageContentExtractor, htmlTextExtractor)
    clearEmailContentFactory = new ClearEmailContentFactory(new MessageParser(), messageContentExtractor, previewFactory)

    textPart = BodyPartBuilder.create()
      .setBody(TEXT_CONTENT, "plain", StandardCharsets.UTF_8)
    htmlPart = BodyPartBuilder.create
      .setBody(HTML_CONTENT, "html", StandardCharsets.UTF_8)
    textAttachment = BodyPartBuilder.create
      .setBody(ATTACHMENT_CONTENT, "plain", StandardCharsets.UTF_8)
      .setContentDisposition("attachment")
  }

  @Test
  def extractShouldSuccessWhenBinaryContentOnly(): Unit = {
    val message: Message = Message.Builder.of
      .setBody(BasicBodyFactory.INSTANCE.binaryBody(BINARY_CONTENT, StandardCharsets.UTF_8))
      .build

    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)

    assertSoftly(softly => {
      softly.assertThat(tryClearEmailContent.isSuccess)
        .isTrue
      softly.assertThat(tryClearEmailContent.get)
        .isEqualTo(ClearEmailContent(
          preview = Preview.EMPTY,
          hasAttachment = false,
          html = "",
          attachments = List.empty))
    })
  }

  @Test
  def extractShouldSuccessWhenMultipartMixed(): Unit = {
    val multipart: Multipart = MultipartBuilder.create("mixed")
      .addBodyPart(textAttachment)
      .addBodyPart(htmlPart)
      .addBodyPart(textPart)
      .build

    val message: Message = Message.Builder.of.setBody(multipart).build
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)

    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    val clearEmailContent: ClearEmailContent = tryClearEmailContent.get

    assertSoftly(softly => {
      softly.assertThat(clearEmailContent.html)
        .isEqualTo(HTML_CONTENT)
      softly.assertThat(clearEmailContent.preview)
        .isEqualTo(previewFactory.fromMime4JMessage(message))
      softly.assertThat(clearEmailContent.hasAttachment)
        .isTrue
      softly.assertThat(clearEmailContent.attachments
        .head
        .getContent
        .contentEquals(ByteSource.wrap(ATTACHMENT_CONTENT.getBytes(StandardCharsets.UTF_8))))
        .isTrue
    })
  }

  @Test
  def attachmentShouldReturnEmptyWhenMessageHaveNotAttachment(): Unit = {
    val message: Message = Message.Builder.of()
      .setSubject("subject")
      .setBody("small message", StandardCharsets.UTF_8)
      .build;
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)

    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    val clearEmailContent: ClearEmailContent = tryClearEmailContent.get
    assertSoftly(softly => {
      softly.assertThat(clearEmailContent.hasAttachment)
        .isFalse
      softly.assertThat(clearEmailContent.attachments.isEmpty)
        .isTrue
    })
  }

  @Test
  def attachmentShouldReturnValueWhenMessageHasSingleAttachment(): Unit = {
    val multipart: Multipart = MultipartBuilder.create("mixed")
      .addBodyPart(textAttachment)
      .addBodyPart(htmlPart)
      .addBodyPart(textPart)
      .build
    val message: Message = Message.Builder.of()
      .setBody(multipart)
      .build
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)

    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    val clearEmailContent: ClearEmailContent = tryClearEmailContent.get
    assertSoftly(softly => {
      softly.assertThat(clearEmailContent.hasAttachment)
        .isTrue
      softly.assertThat(clearEmailContent.attachments.size)
        .isEqualTo(1)
    })
  }

  @Test
  def attachmentShouldReturnValueWhenMessageHasMultiAttachments(): Unit = {
    val multipartBuilder: MultipartBuilder = MultipartBuilder.create("mixed")
    IntStream.range(1, 10)
      .forEach(any => multipartBuilder.addBodyPart(textAttachment))

    val message: Message = Message.Builder.of()
      .setBody(multipartBuilder.build)
      .build
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)

    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    val clearEmailContent: ClearEmailContent = tryClearEmailContent.get

    assertSoftly(softly => {
      softly.assertThat(clearEmailContent.hasAttachment)
        .isTrue
      softly.assertThat(clearEmailContent.attachments.size)
        .isEqualTo(multipartBuilder.getBodyParts.size())
    })
  }

  @Test
  def htmlShouldReturnTextBodyWhenTextOnlyBody(): Unit = {
    val message: Message = Message.Builder.of()
      .setBody(TEXT_CONTENT, StandardCharsets.UTF_8)
      .build

    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)
    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    assertThat(tryClearEmailContent.get.html)
      .isEqualTo(TEXT_CONTENT);
  }

  @Test
  def htmlShouldReturnValueWhenHtmlOnlyBody(): Unit = {
    val message: Message = Message.Builder.of()
      .setBody(HTML_CONTENT, "html", StandardCharsets.UTF_8)
      .build
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)
    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    assertThat(tryClearEmailContent.get.html)
      .isEqualTo(HTML_CONTENT);
  }

  @Test
  def htmlShouldReturnValueWhenMultipartAlternative(): Unit = {
    val multipart: Multipart = MultipartBuilder.create("alternative")
      .addBodyPart(textPart)
      .addBodyPart(htmlPart)
      .build
    val message: Message = Message.Builder.of()
      .setBody(multipart)
      .build
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)
    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    assertThat(tryClearEmailContent.get.html)
      .isEqualTo(HTML_CONTENT)
  }

  @Test
  def previewShouldReturnValueWhenMultipartAlternative(): Unit = {
    val multipart: Multipart = MultipartBuilder.create("alternative")
      .addBodyPart(textPart)
      .addBodyPart(htmlPart)
      .build
    val message: Message = Message.Builder.of
      .setBody(multipart)
      .build
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)
    assertThat(tryClearEmailContent.isSuccess)
      .isTrue

    assertThat(tryClearEmailContent.get.preview)
      .isEqualTo(previewFactory.fromMime4JMessage(message))
  }

  @Test
  def previewShouldReturnEmptyWhenBodyIsEmpty(): Unit = {
    val message: Message = Message.Builder.of
      .setSubject("subject 123")
      .setBody("", StandardCharsets.UTF_8)
      .build
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)
    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    assertThat(tryClearEmailContent.get.preview)
      .isEqualTo(Preview.EMPTY)
  }

  @Test
  def previewShouldReturnValueWhenHtmlOnlyBody(): Unit = {
    val message: Message = Message.Builder.of()
      .setBody(TEXT_CONTENT, StandardCharsets.UTF_8)
      .build
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)
    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    assertThat(tryClearEmailContent.get.preview)
      .isEqualTo(previewFactory.fromMime4JMessage(message))
  }

  @Test
  def previewShouldReturnValueWhenTextOnlyBody(): Unit = {
    val message: Message = Message.Builder.of()
      .setBody(TEXT_CONTENT, StandardCharsets.UTF_8)
      .build
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)
    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    assertThat(tryClearEmailContent.get.preview)
      .isEqualTo(previewFactory.fromMime4JMessage(message))
  }

  @Test
  def previewShouldReturnValueWhenBigHtmlBody(): Unit = {
    val message: Message = Message.Builder.of()
      .setBody(HTML_CONTENT.repeat(999999), "html", StandardCharsets.UTF_8)
      .build
    val tryClearEmailContent: Try[ClearEmailContent] = clearEmailContentFactory.from(message)
    assertThat(tryClearEmailContent.isSuccess)
      .isTrue
    assertThat(tryClearEmailContent.get.preview)
      .isEqualTo(previewFactory.fromMime4JMessage(message))
  }
}
