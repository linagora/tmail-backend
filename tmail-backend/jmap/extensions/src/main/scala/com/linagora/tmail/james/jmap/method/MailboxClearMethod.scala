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

import com.google.common.collect.ImmutableMap
import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.tmail.james.jmap.json.MailboxClearSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_MAILBOX_CLEAR
import com.linagora.tmail.james.jmap.method.MailboxClearMethod.{DELETE_BATCH_SIZE, LOGGER}
import com.linagora.tmail.james.jmap.model.{MailboxClearRequest, MailboxClearResponse}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_SHARES, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, Invocation, SessionTranslator, SetError, UnsignedInt, UrlPrefixes}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.mail.{MailboxGet, UnparsedMailboxId}
import org.apache.james.jmap.method.MailboxSetMethod.assertCapabilityIfSharedMailbox
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.{MailboxId, MessageRange}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageManager, MessageUid}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.{AuditTrail, ReactorUtils}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsObject, Json}
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.OptionConverters.RichOptional

case object MailboxClearCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability = MailboxClearCapability

  override def id(): CapabilityIdentifier = LINAGORA_MAILBOX_CLEAR
}

case object MailboxClearCapability extends Capability {
  val properties: CapabilityProperties = MailboxCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_MAILBOX_CLEAR
}

case object MailboxCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

class MailboxClearCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = MailboxClearCapabilityFactory
}

class MailboxClearMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new MailboxClearCapabilitiesModule())

    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[MailboxClearMethod])
  }
}

object MailboxClearMethod {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[MailboxClearMethod])
  val DELETE_BATCH_SIZE: Int = 256
}

sealed trait MailboxClearResult
case class MailboxClearSuccess(mailboxId: MailboxId, totalDeleted: UnsignedInt) extends MailboxClearResult
case class MailboxClearFailure(mailboxId: UnparsedMailboxId, exception: Throwable) extends MailboxClearResult {
  def asSetError: SetError = exception match {
    case e: MailboxNotFoundException =>
      SetError.notFound(SetErrorDescription(e.getMessage))
    case e: IllegalArgumentException =>
      SetError.invalidArguments(SetErrorDescription(s"${mailboxId.id} is not a mailboxId: ${e.getMessage}"))
    case e =>
      LOGGER.error("Failed to clear mailbox", e)
      SetError.serverFail(SetErrorDescription(e.getMessage))
  }
}

class MailboxClearMethod @Inject()(mailboxManager: MailboxManager,
                                   mailboxIdFactory: MailboxId.Factory,
                                   val metricFactory: MetricFactory,
                                   val serializer: MailboxClearSerializer,
                                   val sessionSupplier: SessionSupplier,
                                   val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[MailboxClearRequest] {

  override val methodName: MethodName = MethodName("Mailbox/clear")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL, LINAGORA_MAILBOX_CLEAR)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, MailboxClearRequest] =
    serializer.deserializeRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext,
                         mailboxSession: MailboxSession, request: MailboxClearRequest): SMono[InvocationWithContext] =
    clearMailbox(request, mailboxSession, capabilities.contains(JAMES_SHARES))
      .map(response => createResponse(invocation.invocation, response))
      .map(InvocationWithContext(_, invocation.processingContext))

  private def clearMailbox(request: MailboxClearRequest, mailboxSession: MailboxSession, supportSharedMailbox: Boolean): SMono[MailboxClearResponse] =
    MailboxGet.parse(mailboxIdFactory)(request.mailboxId)
      .fold(
        e => SMono.just(MailboxClearFailure(request.mailboxId, e)),
        parsedMailboxId => SMono(mailboxManager.getMailboxReactive(parsedMailboxId, mailboxSession))
          .filterWhen(assertCapabilityIfSharedMailbox(mailboxSession, parsedMailboxId, supportSharedMailbox))
          .flatMap(mailbox => expungeAllMessages(mailbox, mailboxSession)
            .map(totalDeleted => MailboxClearSuccess(parsedMailboxId, UnsignedInt.liftOrThrow(totalDeleted))))
          .onErrorResume(e => SMono.just(MailboxClearFailure(request.mailboxId, e))))
      .map {
        case MailboxClearSuccess(_, totalDeleted) => MailboxClearResponse(accountId = request.accountId, totalDeletedMessagesCount = Some(totalDeleted), notCleared = None)
        case failure: MailboxClearFailure => MailboxClearResponse(accountId = request.accountId, totalDeletedMessagesCount = None, notCleared = Some(failure.asSetError))
      }

  private def expungeAllMessages(mailbox: MessageManager, mailboxSession: MailboxSession): SMono[Int] =
    SFlux(Flux.from(mailbox.listMessagesMetadata(MessageRange.all(), mailboxSession))
      .map(_.getComposedMessageId.getUid)
      .window(DELETE_BATCH_SIZE)
      .concatMap(batch => batch.collectList()
        .flatMap(uids => mailbox.deleteReactive(uids.asInstanceOf[java.util.List[MessageUid]], mailboxSession)
          .thenReturn(uids.size()))))
      .reduce(0)((acc: Int, size: Int) => acc + size)
      .flatMap(totalDeleted => auditTrail(mailboxSession, mailbox.getId, totalDeleted)
        .`then`(SMono.just(totalDeleted)))

  private def auditTrail(mailboxSession: MailboxSession, mailboxId: MailboxId, totalDeleted: Int): SMono[Void] =
    SMono(ReactorUtils.logAsMono(() => AuditTrail.entry()
      .username(() => mailboxSession.getUser.asString())
      .protocol("JMAP")
      .action("Mailbox/clear")
      .parameters(() => ImmutableMap.of(
        "loggedInUser", mailboxSession.getLoggedInUser.toScala
          .map(_.asString())
          .getOrElse(""),
        "mailboxId", mailboxId.serialize(),
        "totalDeletedMessages", totalDeleted.toString))
      .log(s"Mailbox clear succeeded. $totalDeleted messages deleted.")))

  private def createResponse(invocation: Invocation, response: MailboxClearResponse): Invocation =
    Invocation(
      methodName = methodName,
      arguments = Arguments(serializer.serializeResponse(response).as[JsObject]),
      methodCallId = invocation.methodCallId)
}

