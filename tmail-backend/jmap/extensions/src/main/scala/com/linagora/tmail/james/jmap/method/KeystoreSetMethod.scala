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
import com.linagora.tmail.james.jmap.json.KeystoreSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_PGP
import com.linagora.tmail.james.jmap.model.{KeystoreSetRequest, KeystoreSetResponse}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{AccountId, Capability, CapabilityFactory, CapabilityProperties, ClientId, Id, Invocation, ServerId, SessionTranslator, SetError, UrlPrefixes}
import org.apache.james.jmap.delegation.ForbiddenAccountManagementException
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.SMono

import scala.jdk.OptionConverters._

case object KeystoreCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object KeystoreCapability extends Capability {
  val properties: CapabilityProperties = KeystoreCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_PGP
}

class KeystoreCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = KeystoreCapabilityFactory
}

case object KeystoreCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability = KeystoreCapability

  override def id(): CapabilityIdentifier = LINAGORA_PGP
}

class KeystoreSetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new KeystoreCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[KeystoreSetMethod])
  }
}

case class KeystoreCreationParseException(setError: SetError) extends Exception

object DelegatedAccountPrecondition {
  def acceptOnlyOwnerRequest(mailboxSession: MailboxSession, requestAccountId: AccountId): Unit = {
    val requestByOwner: Boolean = mailboxSession.getLoggedInUser.toScala
      .flatMap(AccountId.from(_).toOption)
      .contains(requestAccountId)
    if (!requestByOwner) {
      throw ForbiddenAccountManagementException()
    }
  }
}

class KeystoreSetMethod @Inject()(serializer: KeystoreSerializer,
                                  createPerformer: KeystoreSetCreatePerformer,
                                  destroyPerformer: KeystoreSetDestroyPerformer,
                                  val metricFactory: MetricFactory,
                                  val sessionTranslator: SessionTranslator,
                                  val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[KeystoreSetRequest] {
  override val methodName: MethodName = MethodName("Keystore/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_PGP)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: KeystoreSetRequest): SMono[InvocationWithContext] = {
    DelegatedAccountPrecondition.acceptOnlyOwnerRequest(mailboxSession, request.accountId)
    for {
      created <- createPerformer.createKeys(mailboxSession, request)
      destroyed <- destroyPerformer.destroy(mailboxSession, request)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(serializer.serializeKeystoreSetResponse(KeystoreSetResponse(
          accountId = request.accountId,
          created = Some(created.retrieveCreated).filter(_.nonEmpty),
          notCreated = Some(created.retrieveErrors).filter(_.nonEmpty),
          destroyed = Some(destroyed.retrieveDestroyed.map(_.id)).filter(_.nonEmpty))).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = Some(created.retrieveCreated).getOrElse(Map())
        .foldLeft(invocation.processingContext)({
          case (processingContext, (clientId, response)) =>
            Id.validate(response.id.value)
              .fold(_ => processingContext,
                serverId => processingContext.recordCreatedId(ClientId(clientId.id), ServerId(serverId)))
        }))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, KeystoreSetRequest] =
    serializer.deserializeKeystoreSetRequest(invocation.arguments.value).asEitherRequest
}
