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

import com.linagora.tmail.james.jmap.json.JmapSettingsSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_SETTINGS
import com.linagora.tmail.james.jmap.model.SettingsSet.OBJECT_ID
import com.linagora.tmail.james.jmap.model.{SettingsSetError, SettingsSetRequest, SettingsSetResponse, SettingsSetUpdateRequest, SettingsUpdateResponse}
import com.linagora.tmail.james.jmap.settings.{JmapSettingsKey, JmapSettingsRepository, ReadOnlyPropertyProviderAggregator, SettingsTypeName}
import eu.timepit.refined.auto._
import jakarta.inject.{Inject, Named}
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.events.EventBus
import org.apache.james.jmap.InjectionKeys
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.change.{AccountIdRegistrationKey, StateChangeEvent}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object SettingsSetUpdateResults {
  def empty(): SettingsSetUpdateResults =
    SettingsSetUpdateResults(Map(), Map(), None, None)

  def merge(a: SettingsSetUpdateResults, b: SettingsSetUpdateResults) =
    SettingsSetUpdateResults(updateSuccess = a.updateSuccess ++ b.updateSuccess,
      updateFailures = a.updateFailures ++ b.updateFailures,
      newState = newStateFromOnePossibleSingletonSuccessUpdate(a, b),
      oldState = oldStateFromOnePossibleSingletonSuccessUpdate(a, b))

  private def newStateFromOnePossibleSingletonSuccessUpdate(a: SettingsSetUpdateResults, b: SettingsSetUpdateResults): Option[UuidState] =
    a.newState
      .orElse(b.newState)

  private def oldStateFromOnePossibleSingletonSuccessUpdate(a: SettingsSetUpdateResults, b: SettingsSetUpdateResults): Option[UuidState] =
    a.oldState
      .orElse(b.oldState)
}

case class SettingsSetUpdateResults(updateSuccess: Map[String, SettingsUpdateResponse],
                                    updateFailures: Map[String, SettingsSetError],
                                    newState: Option[UuidState],
                                    oldState: Option[UuidState])

sealed trait SettingsUpdateResult {
  def updated: Map[String, SettingsUpdateResponse]
  def notUpdated: Map[String, SettingsSetError]
  def newState: Option[UuidState]
  def oldState: Option[UuidState]

  def asSettingsSetRequestUpdateResults = SettingsSetUpdateResults(updated, notUpdated, newState, oldState)
}

case class SettingsUpdateSuccess(previousState: UuidState, latestState: UuidState) extends SettingsUpdateResult {
  override def updated: Map[String, SettingsUpdateResponse] = Map(OBJECT_ID -> SettingsUpdateResponse(JsObject(Seq())))

  override def notUpdated: Map[String, SettingsSetError] = Map()

  override def newState: Option[UuidState] = Some(latestState)

  override def oldState: Option[UuidState] = Some(previousState)
}

case class SettingsUpdateFailure(id: String, exception: Throwable) extends SettingsUpdateResult {
  override def updated: Map[String, SettingsUpdateResponse] = Map()

  override def notUpdated: Map[String, SettingsSetError] = Map(id -> asSetError(exception))

  def asSetError(exception: Throwable): SettingsSetError = exception match {
      case e: IllegalArgumentException => SettingsSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)))
      case e: Throwable => SettingsSetError.serverFail(Some(SetErrorDescription(e.getMessage)))
    }

  override def newState: Option[UuidState] = None

  override def oldState: Option[UuidState] = None
}

class SettingsSetMethod @Inject()(@Named(InjectionKeys.JMAP) eventBus: EventBus,
                                  val jmapSettingsRepository: JmapSettingsRepository,
                                  val readOnlyPropertyProviderAggregator: ReadOnlyPropertyProviderAggregator,
                                  val metricFactory: MetricFactory,
                                  val sessionSupplier: SessionSupplier,
                                  val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[SettingsSetRequest] {

  override val methodName: MethodName = MethodName("Settings/set")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_SETTINGS)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, SettingsSetRequest] =
    JmapSettingsSerializer.deserializeSetRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: SettingsSetRequest): SMono[InvocationWithContext] = {
    DelegatedAccountPrecondition.acceptOnlyOwnerRequest(mailboxSession, request.accountId)

    (for {
      updateResults <- update(mailboxSession, request)
      currentStateIfAllUpdatesFailed <- retrieveCurrentStateIfAllUpdatesFailed(mailboxSession, updateResults)
      oldState <- evaluateOldState(updateResults, currentStateIfAllUpdatesFailed)
      newState <- evaluateNewState(updateResults, currentStateIfAllUpdatesFailed)
      response = createResponse(invocation.invocation, request, updateResults, oldState, newState)
    } yield {
      dispatchSettingsChangeEvent(mailboxSession.getUser, oldState, newState)
        .`then`(SMono.just(InvocationWithContext(response, invocation.processingContext)))
    })
      .flatMap(publisher => publisher)
  }

  private def dispatchSettingsChangeEvent(username: Username, oldState: UuidState, newState: UuidState): SMono[Void] = {
    def noSettingsChange: Boolean = oldState.equals(newState)

    if (noSettingsChange) {
      SMono.empty
    } else {
      val settingsChangedEvent = StateChangeEvent(eventId = EventId.random(),
        username = username,
        map = Map(SettingsTypeName -> newState))

      SMono(eventBus.dispatch(settingsChangedEvent, AccountIdRegistrationKey(AccountId.fromUsername(username))))
    }
  }

  private def retrieveCurrentStateIfAllUpdatesFailed(mailboxSession: MailboxSession, updateResults: SettingsSetUpdateResults): SMono[Option[UuidState]] =
    SMono.just(updateResults.updateSuccess.isEmpty)
      .filter(allUpdatesFailed => allUpdatesFailed)
      .flatMap(_ => SMono(jmapSettingsRepository.getLatestState(mailboxSession.getUser))
        .map(Some(_)))
      .defaultIfEmpty(None)

  private def evaluateNewState(updateResults: SettingsSetUpdateResults, currentStateIfAllUpdatesFailed: Option[UuidState]): SMono[UuidState] =
    SMono.justOrEmpty(updateResults.newState)
      .switchIfEmpty(SMono.justOrEmpty(currentStateIfAllUpdatesFailed))

  private def evaluateOldState(updateResults: SettingsSetUpdateResults, currentStateIfAllUpdatesFailed: Option[UuidState]): SMono[UuidState] =
    SMono.justOrEmpty(updateResults.oldState)
      .switchIfEmpty(SMono.justOrEmpty(currentStateIfAllUpdatesFailed))

  private def update(mailboxSession: MailboxSession, settingsSetRequest: SettingsSetRequest): SMono[SettingsSetUpdateResults] =
    SFlux.fromIterable(settingsSetRequest.validateId()
      .map[SMono[SettingsUpdateResult]]({
        case (_, Right(updateRequest)) => updateSingletonSettingsObject(mailboxSession, updateRequest)
        case (id, Left(e)) => SMono.just(SettingsUpdateFailure(id, e))
      }))
      .flatMap[SettingsUpdateResult](updateResultMono => updateResultMono)
      .map(updateResult => updateResult.asSettingsSetRequestUpdateResults)
      .reduce[SettingsSetUpdateResults](SettingsSetUpdateResults.empty())(SettingsSetUpdateResults.merge)

  private def updateSingletonSettingsObject(mailboxSession: MailboxSession, updateRequest: SettingsSetUpdateRequest): SMono[SettingsUpdateResult] =
    updateRequest.validate() match {
      case Left(e) => SMono.just(SettingsUpdateFailure(OBJECT_ID, e))
      case Right(validateRequest) => (validateRequest match {
        case validateRequest if validateRequest.isEmpty => doNoop(mailboxSession.getUser)
        case validateRequest if validateRequest.isResetRequest => doUpdateFullReset(mailboxSession.getUser, updateRequest)
        case _ => doUpdatePartial(mailboxSession.getUser, updateRequest)
      }).onErrorResume(error => SMono.just(SettingsUpdateFailure(OBJECT_ID, error)))
    }

  private def doNoop(username: Username): SMono[SettingsUpdateResult] =
    SMono(jmapSettingsRepository.getLatestState(username))
      .map(state => SettingsUpdateSuccess(state, state))

  private def doUpdateFullReset(username: Username, updateRequest: SettingsSetUpdateRequest): SMono[SettingsUpdateResult] =
    SMono.fromCallable(() => updateRequest.getResetRequest)
      .flatMap(SMono.justOrEmpty)
      .flatMap(resetRequest => {
        val readOnlySettingsKeys: Set[JmapSettingsKey] = readOnlyPropertyProviderAggregator.readOnlySettings().asScala.toSet
        val requestedKeys: Set[JmapSettingsKey] = resetRequest.settings.keySet

        val readOnlyRequestedKeys: Set[JmapSettingsKey] = requestedKeys.intersect(readOnlySettingsKeys)

        if (readOnlyRequestedKeys.nonEmpty) {
          val affectedKeys: String = readOnlyRequestedKeys.map(_.asString()).mkString(", ")
          SMono.error(new IllegalArgumentException(s"Cannot modify read-only settings: $affectedKeys"))
        } else {
          SMono.fromPublisher(jmapSettingsRepository.reset(username, resetRequest))
            .map(stateUpdate => SettingsUpdateSuccess(stateUpdate.oldState, stateUpdate.newState))
        }
      })

  private def doUpdatePartial(username: Username, updateRequest: SettingsSetUpdateRequest): SMono[SettingsUpdateResult] =
    SMono.fromCallable(() => updateRequest.getUpdatePartialRequest)
      .flatMap(SMono.justOrEmpty)
      .flatMap(updatePartialRequest => {
        val readOnlySettings: Set[JmapSettingsKey] = readOnlyPropertyProviderAggregator.readOnlySettings().asScala.toSet
        val requestedUpsertKeys: Set[JmapSettingsKey] = updatePartialRequest.toUpsert.settings.keySet
        val requestedRemoveKeys: Set[JmapSettingsKey] = updatePartialRequest.toRemove.toSet

        val readOnlyUpsertKeys: Set[JmapSettingsKey] = requestedUpsertKeys.intersect(readOnlySettings)
        val readOnlyRemoveKeys: Set[JmapSettingsKey] = requestedRemoveKeys.intersect(readOnlySettings)

        if (readOnlyUpsertKeys.nonEmpty || readOnlyRemoveKeys.nonEmpty) {
          val affectedKeys: String = (readOnlyUpsertKeys ++ readOnlyRemoveKeys).map(_.asString()).mkString(", ")
          SMono.error(new IllegalArgumentException(s"Cannot modify read-only settings: $affectedKeys"))
        } else {
          SMono.fromPublisher(jmapSettingsRepository.updatePartial(username, updatePartialRequest))
            .map(stateUpdate => SettingsUpdateSuccess(stateUpdate.oldState, stateUpdate.newState))
        }
      })

  private def createResponse(invocation: Invocation,
                             settingsSetRequest: SettingsSetRequest,
                             updateResult: SettingsSetUpdateResults,
                             oldState: UuidState,
                             newState: UuidState): Invocation = {
    val response = SettingsSetResponse(
      accountId = settingsSetRequest.accountId,
      oldState = oldState,
      newState = newState,
      updated = Some(updateResult.updateSuccess).filter(_.nonEmpty),
      notUpdated = Some(updateResult.updateFailures).filter(_.nonEmpty),
      notCreated = validateNoCreate(settingsSetRequest),
      notDestroyed = validateNoDestroy(settingsSetRequest))

    Invocation(methodName, Arguments(JmapSettingsSerializer.serializeSetResponse(response).as[JsObject]), invocation.methodCallId)
  }

  private def validateNoCreate(settingsSetRequest: SettingsSetRequest): Option[Map[String, SettingsSetError]] =
    settingsSetRequest.create.map(aMap => aMap
      .view
      .mapValues(_ => SettingsSetError.invalidArgument(
        Some(SetErrorDescription("'create' is not supported on singleton objects"))))
      .toMap)

  private def validateNoDestroy(settingsSetRequest: SettingsSetRequest): Option[Map[String, SettingsSetError]] =
    settingsSetRequest.destroy.map(aSet =>
      aSet.map(id => (id, SettingsSetError.invalidArgument(
        Some(SetErrorDescription("'destroy' is not supported on singleton objects")))))
        .toMap)
}