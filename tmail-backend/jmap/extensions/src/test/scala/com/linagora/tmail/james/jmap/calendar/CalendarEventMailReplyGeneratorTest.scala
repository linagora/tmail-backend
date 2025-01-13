/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.calendar

import java.util.Locale

import com.linagora.tmail.james.jmap.calendar.CalendarEventMailReplyGeneratorTest.I18NCalendarEventReplyMessageGeneratorSupporter
import com.linagora.tmail.james.jmap.calendar.CalendarEventReplyGeneratorTest.calendarEventRequestTemplate
import com.linagora.tmail.james.jmap.calendar.I18NCalendarEventReplyMessageGeneratorTest.attendeeReply
import com.linagora.tmail.james.jmap.method.{CalendarEventMailReplyGenerator, I18NCalendarEventReplyMessageGenerator}
import jakarta.mail.internet.MimeMultipart
import net.fortuna.ical4j.model.parameter.PartStat
import org.apache.james.filesystem.api.FileSystem
import org.apache.james.server.core.filesystem.FileSystemImpl
import org.apache.mailet.Mail
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

object CalendarEventMailReplyGeneratorTest {
  class I18NCalendarEventReplyMessageGeneratorSupporter(fileSystem: FileSystem = FileSystemImpl.forTesting(),
                                                        i18nEmlDirectory: String = "classpath://eml/calendar_reply/",
                                                        fakedMailTemplateFileName: String)
    extends I18NCalendarEventReplyMessageGenerator(fileSystem, i18nEmlDirectory) {
    override def evaluateMailTemplateFileName(partStat: PartStat, language: Locale): String = fakedMailTemplateFileName
  }
}

class CalendarEventMailReplyGeneratorTest {

  @Test
  def shouldKeepContentTypeWhenTemplateIsSinglePartHTML(): Unit = {
    val testee: CalendarEventMailReplyGenerator = new CalendarEventMailReplyGenerator(new I18NCalendarEventReplyMessageGeneratorSupporter(fakedMailTemplateFileName = "singlepart_html.eml"))

    val decoratedMessage: Mail = testee.generateMail(calendarEventRequestTemplate, attendeeReply, Locale.ENGLISH).block()

    assertThat(decoratedMessage.getMessage.getContent.asInstanceOf[MimeMultipart].getBodyPart(0).getHeader("Content-Type").head)
      .isEqualTo("text/html; charset=utf-8")
  }
}
