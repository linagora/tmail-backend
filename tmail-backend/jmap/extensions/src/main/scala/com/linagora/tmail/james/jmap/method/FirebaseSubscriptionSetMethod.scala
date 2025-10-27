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

import com.linagora.tmail.james.jmap.json.FirebaseSubscriptionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FIREBASE
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionSetCreatePerformer.CreationResults
import com.linagora.tmail.james.jmap.model.{FirebaseSubscriptionSetRequest, FirebaseSubscriptionSetResponse}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ClientId, Invocation, ServerId}
import org.apache.james.jmap.method.{InvocationWithContext, MethodWithoutAccountId}
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher

class FirebaseSubscriptionSetMethod @Inject()(val serializer: FirebaseSubscriptionSerializer,
                                              val createPerformer: FirebaseSubscriptionSetCreatePerformer,
                                              val updatePerformer: FirebaseSubscriptionSetUpdatePerformer,
                                              val deletePerformer: FirebaseSubscriptionSetDeletePerformer,
                                              val metricFactory: MetricFactory,
                                              val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[FirebaseSubscriptionSetRequest] with Startable {

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_FIREBASE)
  override val methodName: MethodName = MethodName("FirebaseRegistration/set")

  override def getRequest(invocation: Invocation): Either[Exception, FirebaseSubscriptionSetRequest] =
    serializer.deserializeFirebaseSubscriptionSetRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: FirebaseSubscriptionSetRequest): Publisher[InvocationWithContext] =
    for {
      created <- createPerformer.create(request, mailboxSession.getUser)
      updated <- updatePerformer.update(request, mailboxSession.getUser)
      destroyed <- deletePerformer.deleteFirebaseSubscriptions(request, mailboxSession)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(serializer.serialize(FirebaseSubscriptionSetResponse(
          created = created.created.filter(_.nonEmpty),
          notCreated = created.notCreated.filter(_.nonEmpty),
          updated = Some(updated.updated).filter(_.nonEmpty),
          notUpdated = Some(updated.notUpdated).filter(_.nonEmpty),
          destroyed = Some(destroyed.destroyed).filter(_.nonEmpty),
          notDestroyed = Some(destroyed.retrieveErrors).filter(_.nonEmpty)))),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = recordCreationIdInProcessingContext(created, invocation.processingContext))

  private def recordCreationIdInProcessingContext(results: CreationResults, processingContext: ProcessingContext):ProcessingContext =
    results.created.getOrElse(Map())
      .foldLeft(processingContext)({
        case (processingContext, (creationId, result)) =>
          processingContext.recordCreatedId(ClientId(creationId.id), ServerId(result.id.asUnparsedFirebaseSubscriptionId.id))
      })

}
