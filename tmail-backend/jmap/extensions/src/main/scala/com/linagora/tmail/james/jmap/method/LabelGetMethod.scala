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

import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.tmail.james.jmap.json.LabelSerializer
import com.linagora.tmail.james.jmap.label.{LabelChangeRepository, LabelRepository, LabelTypeName}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_LABEL
import com.linagora.tmail.james.jmap.model.{Label, LabelGetRequest, LabelGetResponse, LabelId, LabelIds}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.{TypeName, AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, Invocation, SessionTranslator, UrlPrefixes, UuidState}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

case object LabelCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability = LabelCapability

  override def id(): CapabilityIdentifier = LINAGORA_LABEL
}

case object LabelCapability extends Capability {
  val properties: CapabilityProperties = LabelCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_LABEL
}

case object LabelCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

class LabelCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = LabelCapabilityFactory
}

class LabelMethodModule extends AbstractModule {

  override def configure(): Unit = {
    install(new LabelCapabilitiesModule())

    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[LabelGetMethod])

    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[LabelSetMethod])

    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[LabelChangesMethod])

    Multibinder.newSetBinder(binder(), classOf[TypeName])
      .addBinding()
      .toInstance(LabelTypeName)
  }
}

class LabelGetMethod @Inject()(val labelRepository: LabelRepository,
                               val metricFactory: MetricFactory,
                               val sessionTranslator: SessionTranslator,
                               val sessionSupplier: SessionSupplier,
                               val labelChangeRepository: LabelChangeRepository) extends MethodRequiringAccountId[LabelGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("Label/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_LABEL)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, LabelGetRequest] =
    LabelSerializer.deserializeGetRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: LabelGetRequest): Publisher[InvocationWithContext] =
    request.validateProperties
      .fold(error => SMono.error(error),
        properties => computeResponse(request, mailboxSession)
          .map(response => Invocation(
            methodName,
            Arguments(LabelSerializer.serializeGetResponse(response, properties).as[JsObject]),
            invocation.invocation.methodCallId))
          .map(InvocationWithContext(_, invocation.processingContext)))

  private def computeResponse(request: LabelGetRequest, mailboxSession: MailboxSession): SMono[LabelGetResponse] =
    retrieveLabels(request.ids, mailboxSession.getUser)
      .collectSeq()
      .flatMap(labels => retrieveState(mailboxSession)
        .map(state => LabelGetResponse.from(
          accountId = request.accountId,
          state = state,
          list = labels,
          requestIds = request.ids)))

  private def retrieveLabels(ids: Option[LabelIds], username: Username): SFlux[Label] =
    ids match {
      case None => SFlux(labelRepository.listLabels(username))
      case Some(value) =>
        SFlux(labelRepository.getLabels(username, value.list
          .map(unparsedLabelId => LabelId(unparsedLabelId.id))
          .toSet.asJava))
    }

  private def retrieveState(mailboxSession: MailboxSession): SMono[UuidState] =
    SMono(labelChangeRepository.getLatestState(JavaAccountId.fromUsername(mailboxSession.getUser)))
      .map(UuidState.fromJava)
}