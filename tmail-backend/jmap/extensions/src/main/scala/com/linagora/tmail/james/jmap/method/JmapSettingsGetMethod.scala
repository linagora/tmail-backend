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
import com.linagora.tmail.james.jmap.json.{JmapSettingsSerializer => Serializer}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_SETTINGS
import com.linagora.tmail.james.jmap.model.{JmapSettingsGet, JmapSettingsGetResult, JmapSettingsObject}
import com.linagora.tmail.james.jmap.settings.{JmapSettingsRepository, SettingsTypeName}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, Invocation, SessionTranslator, UrlPrefixes}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

case object JmapSettingsCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = JmapSettingsCapability

  override def id(): CapabilityIdentifier = LINAGORA_SETTINGS
}

case object JmapSettingsCapability extends Capability {
  val properties: CapabilityProperties = JmapSettingsCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_SETTINGS
}

case object JmapSettingsCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

class JmapSettingsCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = JmapSettingsCapabilityFactory
}

class JmapSettingsMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new JmapSettingsCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[JmapSettingsGetMethod])

    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[SettingsSetMethod])

    Multibinder.newSetBinder(binder(), classOf[TypeName])
      .addBinding()
      .toInstance(SettingsTypeName)
  }
}

class JmapSettingsGetMethod @Inject()(val jmapSettingsRepository: JmapSettingsRepository,
                                      val metricFactory: MetricFactory,
                                      val sessionTranslator: SessionTranslator,
                                      val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[JmapSettingsGet] {

  override val methodName: Invocation.MethodName = MethodName("Settings/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_SETTINGS)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, JmapSettingsGet] =
    Serializer.deserializeGetRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: JmapSettingsGet): Publisher[InvocationWithContext] =
    retrieveJmapSettings(mailboxSession.getUser, request)
      .reduce(JmapSettingsGetResult.empty)(JmapSettingsGetResult.merge)
      .map(result => result.asResponse(request.accountId))
      .map(response => Invocation(
        methodName = methodName,
        arguments = Arguments(Serializer.serialize(response).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId))
      .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext))

  private def retrieveJmapSettings(username: Username, request: JmapSettingsGet): SFlux[JmapSettingsGetResult] =
    request.ids match {
      case None => retrieveJmapSettingsSingleton(username).flux()
      case Some(ids) => SFlux.fromIterable(ids)
        .flatMap(id => id match {
          case JmapSettingsObject.SETTING_SINGLETON_ID => retrieveJmapSettingsSingleton(username)
          case _ => SMono.just(JmapSettingsGetResult.notFound(id))
        })
    }

  private def retrieveJmapSettingsSingleton(username: Username): SMono[JmapSettingsGetResult] =
    SMono(jmapSettingsRepository.get(username))
      .map(jmapSettings => JmapSettingsGetResult.singleton(jmapSettings))
      .switchIfEmpty(SMono.just(JmapSettingsGetResult.emptySingleton()))
}