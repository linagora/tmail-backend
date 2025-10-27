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

import java.time.Clock

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionHelper.isInThePast
import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionRepository
import com.linagora.tmail.james.jmap.json.FirebaseSubscriptionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FIREBASE
import com.linagora.tmail.james.jmap.model.{FirebaseSubscription, FirebaseSubscriptionGetRequest, FirebaseSubscriptionGetResponse, FirebaseSubscriptionId, FirebaseSubscriptionIds}
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.method.{InvocationWithContext, MethodWithoutAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class FirebaseSubscriptionGetMethod @Inject()()(val serializer: FirebaseSubscriptionSerializer,
                                                val repository: FirebaseSubscriptionRepository,
                                                val metricFactory: MetricFactory,
                                                val sessionSupplier: SessionSupplier,
                                                val clock: Clock) extends MethodWithoutAccountId[FirebaseSubscriptionGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("FirebaseRegistration/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_FIREBASE)

  override def getRequest(invocation: Invocation): Either[Exception, FirebaseSubscriptionGetRequest] =
    serializer.deserializeFirebaseSubscriptionGetRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: FirebaseSubscriptionGetRequest): Publisher[InvocationWithContext] =
    request.validateProperties
      .fold(e => SMono.error(e),
        properties => retrieveFirebaseSubscription(mailboxSession.getUser, request.ids)
          .collectSeq()
          .map(firebaseSubscriptions => FirebaseSubscriptionGetResponse.from(firebaseSubscriptions, request.ids))
          .map(response => Invocation(
            methodName = methodName,
            arguments = Arguments(serializer.serialize(response, properties).as[JsObject]),
            methodCallId = invocation.invocation.methodCallId))
          .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext)))

  private def retrieveFirebaseSubscription(username: Username, ids: Option[FirebaseSubscriptionIds]): SFlux[FirebaseSubscription] =
    (ids match {
      case None => SFlux(repository.list(username))
      case Some(value) =>
        SFlux(repository.get(username,
          value.list.map(id => FirebaseSubscriptionId.liftOrThrow(id.id.value).toOption)
            .filter(_.isDefined)
            .map(_.get)
            .toSet.asJava))
    })
      .flatMap(subscription => {
        if (isInThePast(subscription.expires, clock)) {
          SMono(repository.revoke(username, subscription.id))
            .`then`(SMono.empty)
        } else {
          SMono.just(subscription)
        }
      })
}
