package com.linagora.tmail.james.jmap.calendar

import java.util.Locale
import java.util.stream.Stream

import com.linagora.tmail.james.jmap.calendar.CalendarEventReplyGeneratorTest.calendarEventRequestTemplate
import com.linagora.tmail.james.jmap.calendar.I18NCalendarEventReplyMessageGeneratorTest.attendeeReply
import com.linagora.tmail.james.jmap.method.I18NCalendarEventReplyMessageGenerator
import com.linagora.tmail.james.jmap.model.AttendeeReply
import jakarta.mail.internet.{MimeMessage, MimeMultipart}
import net.fortuna.ical4j.model.parameter.PartStat
import org.apache.james.core.Username
import org.apache.james.server.core.filesystem.FileSystemImpl
import org.apache.james.util.MimeMessageUtil
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}

object I18NCalendarEventReplyMessageGeneratorTest {
  val attendeeReply: AttendeeReply = AttendeeReply(Username.of("bob@domain.com"), PartStat.DECLINED)

  def i18nFileNameTestSuite: Stream[Arguments] = {
    Stream.of(
      Arguments.of(Locale.ENGLISH, PartStat.DECLINED, "calendar_reply_declined-en.eml"),
      Arguments.of(Locale.FRANCE, PartStat.ACCEPTED, "calendar_reply_accepted-fr.eml"),
      Arguments.of(Locale.ENGLISH, PartStat.TENTATIVE, "calendar_reply_tentative-en.eml"),
      Arguments.of(Locale.FRANCE, PartStat.DECLINED, "calendar_reply_declined-fr.eml"),
      Arguments.of(Locale.ENGLISH, PartStat.ACCEPTED, "calendar_reply_accepted-en.eml"))
  }
}

class I18NCalendarEventReplyMessageGeneratorTest {

  val testee: I18NCalendarEventReplyMessageGenerator = new I18NCalendarEventReplyMessageGenerator(FileSystemImpl.forTesting(), "classpath://eml/calendar_reply/")

  @Test
  def shouldDecoratedSubjectWhenMultipart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "multipart.eml").block()

    assertThat(decoratedMessage.getSubject)
      .isEqualTo("Accepted: Simple event @ Fri Feb 23, 2024 (bob@domain.com)")
  }

  @Test
  def shouldDecoratedTextPlainBodyPartWhenMultipart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "multipart.eml").block()

    assertThat(decoratedMessage.getContent)
      .isInstanceOf(classOf[MimeMultipart])
    assertThat(decoratedMessage.getContent.asInstanceOf[MimeMultipart].getBodyPart(0).getContent)
      .isEqualTo("bob@domain.com has accepted this invitation.\n")
  }

  @Test
  def shouldDecoratedTextHTMLBodyPartWhenMultipart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "multipart_html.eml").block()

    assertThat(decoratedMessage.getContent)
      .isInstanceOf(classOf[MimeMultipart])
    assertThat(decoratedMessage.getContent.asInstanceOf[MimeMultipart].getBodyPart(0).getContent)
      .isEqualTo("""<!DOCTYPE html>
                   |<html lang="en">
                   |<head>
                   |    <meta charset="UTF-8">
                   |    <title>Example Email</title>
                   |</head>
                   |<body>
                   |    <h1>Hello, comptetest15.linagora@example.com!</h1>
                   |    <p>This is an example HTML email.</p>
                   |    <p>For more information, visit <a href="https://example.com">example.com</a>.</p>
                   |</body>
                   |</html>
                   |""".stripMargin)
  }

  @Test
  def shouldDecoratedTextHTMLBodyPartWhenSinglepart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "singlepart_html.eml").block()

    assertThat(decoratedMessage.getContent)
      .isInstanceOf(classOf[String])
    assertThat(decoratedMessage.getContent.asInstanceOf[String])
      .isEqualTo("""<!DOCTYPE html>
                   |<html lang="en">
                   |<head>
                   |    <meta charset="UTF-8">
                   |    <title>Example HTML Email</title>
                   |</head>
                   |<body>
                   |    <h1>Hello, comptetest15.linagora@example.com!</h1>
                   |    <p>This is an example HTML email.</p>
                   |    <p>For more information, visit <a href="https://example.com">example.com</a>.</p>
                   |</body>
                   |</html>
                   |""".stripMargin)
  }

  @Test
  def shouldDecoratedMessagesWhenSinglePart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "single.eml").block()

    assertThat(decoratedMessage.getSubject)
      .isEqualTo("Accepted: Simple event @ Fri Feb 23, 2024 (bob@domain.com)")
    assertThat(decoratedMessage.getContent.asInstanceOf[String])
      .isEqualTo("bob@domain.com has accepted this invitation.\n")
  }

  @ParameterizedTest
  @MethodSource(value = Array("i18nFileNameTestSuite"))
  def evaluatedMessageFileNameShouldSupportI18n(locale: Locale, partStat: PartStat, expectedFileName: String): Unit = {
    assertThat(testee.evaluateMailTemplateFileName(partStat, locale))
      .isEqualTo(expectedFileName)
  }

  @Test
  def shouldKeepAnotherPartWhenMultipart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "multipart.eml").block()

    val originalMessage: MimeMessage = MimeMessageUtil.mimeMessageFromStream(ClassLoader.getSystemResourceAsStream("eml/calendar_reply/multipart.eml"))

    assertThat(decoratedMessage.getContent.asInstanceOf[MimeMultipart].getCount)
      .isEqualTo(originalMessage.getContent.asInstanceOf[MimeMultipart].getCount)
  }

  @Test
  def getBasedMimeMessageShouldThrowWhenTemplateNotFound(): Unit = {
    val throwingCallable: ThrowingCallable = () => testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "unknown.eml").block()
    assertThatThrownBy(throwingCallable)
      .isInstanceOf(classOf[IllegalArgumentException])
    assertThatThrownBy(throwingCallable)
      .hasMessageContaining("Cannot find the template eml file: classpath://eml/calendar_reply/unknown.eml")
  }
}
