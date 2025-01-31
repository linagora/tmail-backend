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

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.json.{LabelChangesSerializer => Serializer}
import com.linagora.tmail.james.jmap.label.LabelChangeRepository
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_LABEL
import com.linagora.tmail.james.jmap.model.{LabelChanges, LabelChangesRequest => Request, LabelChangesResponse => Response}
import eu.timepit.refined.auto._
import org.apache.james.jmap.api.change.{State => JavaState}
import org.apache.james.jmap.api.exception.ChangeNotFoundException
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ErrorCode, Invocation, SessionTranslator}
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class LabelChangesMethod @Inject()(val metricFactory: MetricFactory,
                                   val sessionSupplier: SessionSupplier,
                                   val sessionTranslator: SessionTranslator,
                                   val labelChangeRepository: LabelChangeRepository) extends MethodRequiringAccountId[Request] {

  override val methodName: Invocation.MethodName = MethodName("Label/changes")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_LABEL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: Request): Publisher[InvocationWithContext] =
    retrieveChanges(mailboxSession, request)
      .map(Response.from(request.accountId, request.sinceState, _))
      .map(res => InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(Serializer.serialize(res)),
          methodCallId = invocation.invocation.methodCallId),
        processingContext = invocation.processingContext))
      .onErrorResume {
        case e: ChangeNotFoundException => SMono.just(InvocationWithContext(Invocation.error(ErrorCode.CannotCalculateChanges, e.getMessage, invocation.invocation.methodCallId), invocation.processingContext))
        case e => SMono.error(e)
      }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, Request] =
    Serializer.deserializeRequest(invocation.arguments.value).asEitherRequest

  private def retrieveChanges(mailboxSession: MailboxSession, request: Request): SMono[LabelChanges] =
    SMono(labelChangeRepository.getSinceState(
      accountId = JavaAccountId.fromUsername(mailboxSession.getUser),
      state = JavaState.of(request.sinceState.value),
      maxIdsToReturn = request.maxChanges))
}
