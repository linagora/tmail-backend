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
import com.linagora.tmail.james.jmap.json.ForwardSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FORWARD
import com.linagora.tmail.james.jmap.model.Forwards.UNPARSED_SINGLETON
import com.linagora.tmail.james.jmap.model.{ForwardGetRequest, ForwardGetResponse, ForwardNotFound, Forwards, UnparsedForwardId}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.core.{AccountId, Capability, CapabilityFactory, CapabilityProperties, ErrorCode, Invocation, MissingCapabilityException, Properties, SessionTranslator, UrlPrefixes}
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.rrt.api.RecipientRewriteTable
import org.apache.james.rrt.lib.{Mapping, MappingSource}
import org.apache.james.util.ReactorUtils
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.StreamConverters._

case object ForwardCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object ForwardCapability extends Capability {
  val properties: CapabilityProperties = ForwardCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_FORWARD
}

class ForwardCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = ForwardCapabilityFactory
}

case object ForwardCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability = ForwardCapability

  override def id(): CapabilityIdentifier = LINAGORA_FORWARD
}

class ForwardGetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new ForwardCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[ForwardGetMethod])
  }
}

object ForwardGetResult {
  def empty: ForwardGetResult = ForwardGetResult(Set.empty, ForwardNotFound(Set.empty))
  def merge(result1: ForwardGetResult, result2: ForwardGetResult): ForwardGetResult = result1.merge(result2)
  def found(forwards: Forwards): ForwardGetResult =
    ForwardGetResult(Set(forwards), ForwardNotFound(Set.empty))
  def notFound(forwardId: UnparsedForwardId): ForwardGetResult =
    ForwardGetResult(Set.empty, ForwardNotFound(Set(forwardId)))
}

case class ForwardGetResult(forwards: Set[Forwards], notFound: ForwardNotFound) {
  def merge(other: ForwardGetResult): ForwardGetResult =
    ForwardGetResult(this.forwards ++ other.forwards, this.notFound.merge(other.notFound))
  def asResponse(accountId: AccountId): ForwardGetResponse =
    ForwardGetResponse(
      accountId = accountId,
      state = INSTANCE,
      list = forwards.toList,
      notFound = notFound)
}

class ForwardGetMethod @Inject()(recipientRewriteTable: RecipientRewriteTable,
                                 val metricFactory: MetricFactory,
                                 val sessionTranslator: SessionTranslator,
                                 val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[ForwardGetRequest] {
  override val methodName: Invocation.MethodName = MethodName("Forward/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_FORWARD)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: ForwardGetRequest): SMono[InvocationWithContext] = {
    val requestedProperties: Properties = request.properties.getOrElse(Forwards.allProperties)
    (requestedProperties -- Forwards.allProperties match {
      case validProperties if validProperties.isEmpty() => getForwards(request, mailboxSession)
        .reduce(ForwardGetResult.empty)(ForwardGetResult.merge)
        .map(forwardResult => forwardResult.asResponse(request.accountId))
        .map(forwardGetResponse => Invocation(
          methodName = methodName,
          arguments = Arguments(ForwardSerializer.serializeForwardGetResponse(forwardGetResponse, requestedProperties).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId))
      case invalidProperties: Properties => SMono.just(Invocation.error(errorCode = ErrorCode.InvalidArguments,
        description = s"The following properties [${invalidProperties.format}] do not exist.",
        methodCallId = invocation.invocation.methodCallId))
    }).map(InvocationWithContext(_, invocation.processingContext))
      .onErrorResume{ case e: Exception => handleRequestValidationErrors(e, invocation.invocation.methodCallId)
        .map(errorInvocation => InvocationWithContext(errorInvocation,  invocation.processingContext))}
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, ForwardGetRequest] =
    ForwardSerializer.deserializeForwardGetRequest(invocation.arguments.value).asEitherRequest

  private def handleRequestValidationErrors(exception: Exception, methodCallId: MethodCallId): SMono[Invocation] = exception match {
    case _: MissingCapabilityException => SMono.just(Invocation.error(ErrorCode.UnknownMethod, methodCallId))
    case e: IllegalArgumentException => SMono.just(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, methodCallId))
  }

  private def getForwards(forwardGetRequest: ForwardGetRequest, mailboxSession: MailboxSession): SFlux[ForwardGetResult] =
    forwardGetRequest.ids match {
      case None => getForwardsSingleton(mailboxSession)
        .map(ForwardGetResult.found)
        .flux()
      case Some(ids) => SFlux.fromIterable(ids.value)
        .flatMap(id => id match {
          case UNPARSED_SINGLETON => getForwardsSingleton(mailboxSession)
            .map(ForwardGetResult.found)
          case _ => SMono.just(ForwardGetResult.notFound(id))
        })
    }

  private def getForwardsSingleton(mailboxSession: MailboxSession): SMono[Forwards] = {
    val userMailAddress: MailAddress = mailboxSession.getUser.asMailAddress
    SMono.fromCallable(() => recipientRewriteTable.getStoredMappings(MappingSource.fromMailAddress(userMailAddress))
        .select(Mapping.Type.Forward)
        .asStream()
        .map(mapping => mapping.asMailAddress()
          .orElseThrow(() => new IllegalStateException(s"Can not compute address for mapping ${mapping.asString}")))
        .toScala(List))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .map(mappings => Forwards.asRfc8621(mappings, userMailAddress))
  }
}
