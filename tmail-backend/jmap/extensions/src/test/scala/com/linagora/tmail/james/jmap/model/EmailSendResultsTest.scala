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

package com.linagora.tmail.james.jmap.model

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.method.{EmailSubmissionException, ForbiddenFromException, ForbiddenMailFromException, MissingFromHeaderException, NoRecipientException, SizeExceededException}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EmailSendResultsTest {
  private val creationId = EmailSendCreationId("c1")

  @Test
  def shouldReportForbiddenHeaders(): Unit =
    assertSubmissionError(ForbiddenFromException(List("other@example.com"), Properties("sender")))

  @Test
  def shouldReportForbiddenEnvelope(): Unit =
    assertSubmissionError(ForbiddenMailFromException("other@example.com"))

  @Test
  def shouldReportMissingFrom(): Unit =
    assertSubmissionError(MissingFromHeaderException())

  @Test
  def shouldReportNoRecipients(): Unit =
    assertSubmissionError(NoRecipientException())

  @Test
  def shouldPreserveInvalidRequest(): Unit = {
    val error = SetError.invalidArguments(SetErrorDescription("Invalid request"), Some(Properties("email/create")))
    assertError(EmailSendCreationRequestInvalidException(error), error)
  }

  @Test
  def shouldPreserveTooLarge(): Unit = {
    val exception = SizeExceededException(100, 50)
    assertError(exception, SetError.tooLarge(SetErrorDescription(exception.getMessage)))
  }

  @Test
  def shouldPreserveUnexpectedFailure(): Unit =
    assertError(new IllegalStateException("Queue unavailable"), SetError.serverFail(SetErrorDescription("Queue unavailable")))

  private def assertSubmissionError(exception: EmailSubmissionException): Unit =
    assertError(exception, exception.asSetError)

  private def assertError(exception: Throwable, expected: SetError): Unit = {
    val result = EmailSendResults.notCreated(creationId, exception)
    assertThat(result.notCreated).isEqualTo(Some(Map(creationId -> expected)))
    assertThat(result.created.isEmpty).isTrue()
    assertThat(result.creationIdResolver.isEmpty).isTrue()
  }
}
