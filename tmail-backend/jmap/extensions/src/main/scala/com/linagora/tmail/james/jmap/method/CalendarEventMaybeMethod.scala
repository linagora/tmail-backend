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

package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.CalendarEventReplySerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CALENDAR
import com.linagora.tmail.james.jmap.model.{CalendarEventNotParsable, CalendarEventReplyMaybeResponse, CalendarEventReplyRequest, CalendarEventReplyResults}
import com.linagora.tmail.james.jmap.{AttendanceStatus, EventAttendanceRepository}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, SessionTranslator}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.SMono

import scala.compat.java8.OptionConverters
import scala.jdk.CollectionConverters._

class CalendarEventMaybeMethod @Inject()(val eventAttendanceRepository: EventAttendanceRepository,
                                         val metricFactory: MetricFactory,
                                         val sessionTranslator: SessionTranslator,
                                         val sessionSupplier: SessionSupplier,
                                         val supportedLanguage: CalendarEventReplySupportedLanguage) extends MethodRequiringAccountId[CalendarEventReplyRequest] {


  override val methodName: Invocation.MethodName = MethodName("CalendarEvent/maybe")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_CALENDAR)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext,
                         mailboxSession: MailboxSession, request: CalendarEventReplyRequest): Publisher[InvocationWithContext] =
    CalendarEventReplyRequest.extractParsedBlobIds(request) match {
      case (notParsable: CalendarEventNotParsable, blobIdList: Seq[BlobId]) =>
        SMono(eventAttendanceRepository.setAttendanceStatus(mailboxSession.getUser, AttendanceStatus.Tentative, blobIdList.asJava, OptionConverters.toJava(request.language)))
          .map(result => CalendarEventReplyResults.merge(result, CalendarEventReplyResults.notDone(notParsable)))
          .map(result => CalendarEventReplyMaybeResponse.from(request.accountId, result))
          .map(response => Invocation(
            methodName,
            Arguments(CalendarEventReplySerializer.serialize(response).as[JsObject]),
            invocation.invocation.methodCallId))
          .map(InvocationWithContext(_, invocation.processingContext))
    }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, CalendarEventReplyRequest] =
    CalendarEventReplySerializer.deserializeRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)
      .flatMap(_.validate(supportedLanguage.valueAsStringSet))
}