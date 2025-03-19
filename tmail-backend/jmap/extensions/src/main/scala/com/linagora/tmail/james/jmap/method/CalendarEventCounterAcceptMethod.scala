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

import com.linagora.tmail.james.jmap.json.{CalendarEventCounterSerializer => Serializer}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CALENDAR
import com.linagora.tmail.james.jmap.model.{CalendarEventNotParsable, CalendarEventCounterAcceptRequest => CounterAcceptRequest, CalendarEventCounterAcceptedResponse => Response}
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

class CalendarEventCounterAcceptMethod @Inject()(val counterPerformer: CalendarEventCounterPerformer,
                                                 val metricFactory: MetricFactory,
                                                 val sessionTranslator: SessionTranslator,
                                                 val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[CounterAcceptRequest] {

  override val methodName: Invocation.MethodName = MethodName("CalendarEventCounter/accept")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_CALENDAR)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, CounterAcceptRequest] =
    Serializer.deserializeAcceptRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)
      .flatMap(_.validate)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: CounterAcceptRequest): Publisher[InvocationWithContext] =

    CounterAcceptRequest.extractParsedBlobIds(request) match {
      case (notParsable: CalendarEventNotParsable, blobIdList: Seq[BlobId]) =>
        counterPerformer.accept(mailboxSession, blobIdList)
          .map(result => EventCounterAcceptedResults.merge(result, EventCounterAcceptedResults.notDone(notParsable)))
          .map(result => Response.from(request.accountId, result))
          .map(response => Invocation(
            methodName,
            Arguments(Serializer.serialize(response).as[JsObject]),
            invocation.invocation.methodCallId))
          .map(InvocationWithContext(_, invocation.processingContext))
    }
}
