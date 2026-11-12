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

import java.util.concurrent.ConcurrentLinkedDeque

import com.google.common.collect.ImmutableMap
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.CheckedRefined.given
import com.linagora.tmail.james.jmap.json.ForwardSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FORWARD
import com.linagora.tmail.james.jmap.method.ForwardSetMethod.{ForwardAddition, ForwardChange, ForwardRemoval, LOGGER}
import com.linagora.tmail.james.jmap.model.{ForwardId, ForwardSetError, Forwards, ForwardSetRequest, ForwardSetResponse, ForwardSetUpdateFailure, ForwardSetUpdateResult, ForwardSetUpdateResults, ForwardSetUpdateSuccess, ForwardUpdateRequest}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.{SetErrorDescription, invalidArgumentValue}
import org.apache.james.jmap.core.{Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.rrt.api.{MappingAlreadyExistsException, RecipientRewriteTable}
import org.apache.james.rrt.lib.Mapping.Type
import org.apache.james.rrt.lib.{Mapping, MappingSource, Mappings}
import org.apache.james.util.{AuditTrail, ReactorUtils}
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.jdk.StreamConverters.StreamHasToScala

class ForwardSetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[ForwardSetMethod])
  }
}

object ForwardSetMethod {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[ForwardSetMethod])

  private sealed trait ForwardChange {
    def destination: MailAddress

    def perform(recipientRewriteTable: RecipientRewriteTable, source: MappingSource): Unit

    // Undo must be idempotent: the change may have failed before, or after, being (partially) stored
    def undo(recipientRewriteTable: RecipientRewriteTable, source: MappingSource): Unit

    def modifies(storedMappings: Mappings): Boolean

    protected def mapping: Mapping = Mapping.forward(destination.asString())
  }

  private case class ForwardAddition(destination: MailAddress) extends ForwardChange {
    override def perform(recipientRewriteTable: RecipientRewriteTable, source: MappingSource): Unit =
      recipientRewriteTable.addForwardMapping(source, destination.asString())

    override def undo(recipientRewriteTable: RecipientRewriteTable, source: MappingSource): Unit =
      recipientRewriteTable.removeForwardMapping(source, destination.asString())

    override def modifies(storedMappings: Mappings): Boolean = !storedMappings.contains(mapping)
  }

  private case class ForwardRemoval(destination: MailAddress) extends ForwardChange {
    override def perform(recipientRewriteTable: RecipientRewriteTable, source: MappingSource): Unit =
      recipientRewriteTable.removeForwardMapping(source, destination.asString())

    override def undo(recipientRewriteTable: RecipientRewriteTable, source: MappingSource): Unit =
      try {
        recipientRewriteTable.addForwardMapping(source, destination.asString())
      } catch {
        case _: MappingAlreadyExistsException => () // the removal did not take effect: nothing to restore
      }

    override def modifies(storedMappings: Mappings): Boolean = storedMappings.contains(mapping)
  }
}

class ForwardSetMethod @Inject()(recipientRewriteTable: RecipientRewriteTable,
                                 val metricFactory: MetricFactory,
                                 val sessionTranslator: SessionTranslator,
                                 val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[ForwardSetRequest] {

  override val methodName: MethodName = MethodName("Forward/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_FORWARD)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, ForwardSetRequest] =
    ForwardSerializer.deserializeForwardSetRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: ForwardSetRequest): Publisher[InvocationWithContext] = {
    DelegatedAccountPrecondition.acceptOnlyOwnerRequest(mailboxSession, request.accountId)
    updateForward(request, mailboxSession)
      .map(updateResult => createResponse(invocation.invocation, request, updateResult))
      .map(InvocationWithContext(_, invocation.processingContext))
  }

  private def updateForward(request: ForwardSetRequest, mailboxSession: MailboxSession): SMono[ForwardSetUpdateResults] =
    SFlux.fromIterable(request.parseUpdateRequest())
      .flatMap {
        case (id, Right(patch)) => patch.asForwardUpdateRequest
          .fold(error => SMono.just(ForwardSetUpdateFailure(id, error)),
            validPatch => update(mailboxSession.getUser, validPatch))
        case (id, Left(error)) => SMono.just(ForwardSetUpdateFailure(id, error))
      }
      .map(_.asForwardSetUpdateResult)
      .foldWith[ForwardSetUpdateResults](ForwardSetUpdateResults.empty())(ForwardSetUpdateResults.merge)

  private def update(username: Username, updateRequest: ForwardUpdateRequest): SMono[ForwardSetUpdateResult] = {
    val mappingSource: MappingSource = MappingSource.fromUser(username)
    retrieveMappings(mappingSource)
      .flatMap(currentForwards => {
        val currentState: Forwards = Forwards.asRfc8621(currentForwards.toList, username.asMailAddress())
        update(mappingSource, currentForwards, getForwardDestinations(updateRequest.applyTo(currentState), username))
      })
      .onErrorResume(error => SMono.just[ForwardSetUpdateResult](ForwardSetUpdateFailure(ForwardId.asString, error)))
  }

  // MailAddress equality ignores case: distinct prevents storing the same destination twice (eg ALICE@domain.tld, alice@domain.tld)
  private def getForwardDestinations(forwards: Forwards, currentUser: Username): Seq[MailAddress] = {
    val self: MailAddress = currentUser.asMailAddress()
    val destinations: Seq[MailAddress] = forwards.forwards.map(_.value)
      .filterNot(_.equals(self))
      .distinct
    val localCopy: Option[MailAddress] = Option.when(forwards.localCopy.value && destinations.nonEmpty)(self)
    destinations ++ localCopy
  }

  private def createResponse(invocation: Invocation,
                             forwardSetRequest: ForwardSetRequest,
                             updateResult: ForwardSetUpdateResults): Invocation = {
    val response: ForwardSetResponse = ForwardSetResponse(
      accountId = forwardSetRequest.accountId,
      newState = UuidState.INSTANCE,
      updated = Some(updateResult.updateSuccess).filter(_.nonEmpty),
      notUpdated = Some(updateResult.updateFailures).filter(_.nonEmpty),
      notCreated = validateNoCreate(forwardSetRequest),
      notDestroyed = validateNoDestroy(forwardSetRequest))

    Invocation(methodName,
      Arguments(ForwardSerializer.serializeForwardSetResponse(response).as[JsObject]),
      invocation.methodCallId)
  }

  private def update(mappingSource: MappingSource, currentForwards: Seq[MailAddress], forwardDestinations: Seq[MailAddress]): SMono[ForwardSetUpdateResult] = {
    val changes: Seq[ForwardChange] = currentForwards.diff(forwardDestinations).map(ForwardRemoval(_)) ++
      forwardDestinations.diff(currentForwards).map(ForwardAddition(_))
    val attemptedChanges: ConcurrentLinkedDeque[ForwardChange] = new ConcurrentLinkedDeque()

    SFlux.fromIterable(changes)
      .concatMap(change => SMono.fromCallable(() => attempt(change, mappingSource, attemptedChanges)))
      .`then`()
      .doOnSuccess(_ -> AuditTrail.entry()
        .username(() => mappingSource.asUsername().toScala.map(_.asString()).getOrElse(""))
        .protocol("JMAP")
        .action("ForwardSet")
        .parameters(() => ImmutableMap.of("mappingSource", mappingSource.asUsername().toScala.map(_.asString()).getOrElse(""),
          "forwardList", forwardDestinations.map(_.asString).mkString(",")))
        .log("Update forward."))
      .`then`(SMono.just[ForwardSetUpdateResult](ForwardSetUpdateSuccess))
      .onErrorResume(error => revert(mappingSource, attemptedChanges.asScala.toSeq, error)
        .`then`(SMono.just[ForwardSetUpdateResult](ForwardSetUpdateFailure(ForwardId.asString, error))))
  }

  // The change is journaled before being performed, so that a failing change, which may be partially stored, is reverted too.
  // It is journaled only if it modifies the stored forwards, so that a revert never undoes a concurrent request.
  private def attempt(change: ForwardChange, mappingSource: MappingSource, attemptedChanges: ConcurrentLinkedDeque[ForwardChange]): Unit = {
    if (change.modifies(recipientRewriteTable.getStoredMappings(mappingSource))) {
      attemptedChanges.push(change)
    }
    change.perform(recipientRewriteTable, mappingSource)
  }

  // Undo only the changes this request attempted, most recent first: changes made concurrently by other requests are kept
  private def revert(mappingSource: MappingSource, attemptedChanges: Seq[ForwardChange], updateError: Throwable): SMono[Unit] =
    SFlux.fromIterable(attemptedChanges)
      .concatMap(change => SMono.fromCallable(() => change.undo(recipientRewriteTable, mappingSource))
        .onErrorResume(revertError => {
          LOGGER.error("Failed to revert {} of {} after a failed Forward/set: forwards are left partially updated",
            change, mappingSource.asString(), revertError)
          updateError.addSuppressed(revertError)
          SMono.empty
        }))
      .`then`()

  private def retrieveMappings(mappingSource: MappingSource): SMono[Seq[MailAddress]] =
    SMono.fromCallable(() => recipientRewriteTable.getStoredMappings(mappingSource)
      .select(Type.Forward)
      .asStream()
      .map[MailAddress](mapping => mapping.asMailAddress()
        .orElseThrow(() => new IllegalStateException(s"Can not compute address for mapping ${mapping.asString}")))
      .toScala(Seq))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  private def validateNoCreate(forwardSetRequest: ForwardSetRequest): Option[Map[String, ForwardSetError]] =
    forwardSetRequest.create.map(aMap => aMap
      .view
      .mapValues(_ => ForwardSetError(invalidArgumentValue, Some(SetErrorDescription("'create' is not supported on singleton objects"))))
      .toMap)


  private def validateNoDestroy(forwardSetRequest: ForwardSetRequest): Option[Map[String, ForwardSetError]] =
    forwardSetRequest.destroy.map(aSet =>
      aSet.map(id => (id, ForwardSetError(invalidArgumentValue, Some(SetErrorDescription("'destroy' is not supported on singleton objects")))))
        .toMap)

}
