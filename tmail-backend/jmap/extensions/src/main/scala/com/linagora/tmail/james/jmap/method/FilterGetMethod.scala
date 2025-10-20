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
import com.linagora.tmail.james.jmap.json.FilterSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FILTER
import com.linagora.tmail.james.jmap.model.{Filter, FilterGetNotFound, FilterGetRequest, FilterGetResponse, FilterState, FilterWithVersion, Rule}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.filtering.FilteringManagement
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, Invocation, SessionTranslator, UrlPrefixes}
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MailboxId
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

case object FilterCapabilityProperties extends CapabilityProperties {
  private val VERSION = 2

  override def jsonify(): JsObject = Json.obj("version" -> VERSION)
}

case object FilterCapability extends Capability {
  val properties: CapabilityProperties = FilterCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_FILTER
}

class FilterCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = FilterCapabilityFactory
}

case object FilterCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability = FilterCapability

  override def id(): CapabilityIdentifier = LINAGORA_FILTER
}

class FilterGetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new FilterCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[FilterGetMethod])
  }
}

class FilterGetMethod @Inject()(val metricFactory: MetricFactory,
                                val sessionSupplier: SessionSupplier,
                                val mailboxIdFactory: MailboxId.Factory,
                                val sessionTranslator: SessionTranslator,
                                filteringManagement: FilteringManagement) extends MethodRequiringAccountId[FilterGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("Filter/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(LINAGORA_FILTER)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession,
                         request: FilterGetRequest): Publisher[InvocationWithContext] =
    getFilterGetResponse(request, mailboxSession).map(response => InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(FilterSerializer(mailboxIdFactory).serializeFilterGetResponse(response).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = invocation.processingContext))


  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, FilterGetRequest] =
    FilterSerializer(mailboxIdFactory).deserializeFilterGetRequest(invocation.arguments.value)
      .asEitherRequest

  private def retrieveFiltersWithVersion(username: Username) : SMono[FilterWithVersion] =
    SMono.fromPublisher(filteringManagement.listRulesForUser(username))
      .map(rulesWithVersion => FilterWithVersion(
        Filter("singleton", rulesWithVersion.getRules.asScala.toList.map(rule => Rule.fromJava(rule, mailboxIdFactory))),
        rulesWithVersion.getVersion))

  private def getFilterGetResponse(request: FilterGetRequest,
                                   mailboxSession: MailboxSession): SMono[FilterGetResponse] =
    request.ids match {
      case None => retrieveFiltersWithVersion(mailboxSession.getUser)
        .map(filterWithVersion => FilterGetResponse(request.accountId, FilterState(filterWithVersion.version.asInteger), List(filterWithVersion.filter), FilterGetNotFound(List())))
      case Some(ids) => if(ids.value.contains("singleton")) {
        retrieveFiltersWithVersion(mailboxSession.getUser)
          .map(filterWithVersion => FilterGetResponse(request.accountId, FilterState(filterWithVersion.version.asInteger), List(filterWithVersion.filter), FilterGetNotFound(ids.value.filterNot(id => id.equals("singleton")))))
      } else {
        retrieveFiltersWithVersion(mailboxSession.getUser)
          .map(filterWithVersion => FilterGetResponse(request.accountId, FilterState(filterWithVersion.version.asInteger), List(), FilterGetNotFound(ids.value)))
      }
    }

}

