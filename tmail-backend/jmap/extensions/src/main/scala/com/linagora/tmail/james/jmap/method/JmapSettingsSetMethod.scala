package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.JmapSettingsSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_SETTINGS
import com.linagora.tmail.james.jmap.model.SettingsSet.OBJECT_ID
import com.linagora.tmail.james.jmap.model.{SettingsSetError, SettingsSetRequest, SettingsSetResponse, SettingsUpdateResponse}
import com.linagora.tmail.james.jmap.settings.{JmapSettingsRepository, JmapSettingsUpsertRequest}
import eu.timepit.refined.auto._
import javax.inject.Inject
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

class SettingsSetRequestSetMethod @Inject()(val jmapSettingsRepository: JmapSettingsRepository,
                                            val metricFactory: MetricFactory,
                                            val sessionSupplier: SessionSupplier,
                                            val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[SettingsSetRequest] {

  override val methodName: MethodName = MethodName("Settings/set")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_SETTINGS)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, SettingsSetRequest] =
    JmapSettingsSerializer.deserializeSetRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: SettingsSetRequest): SMono[InvocationWithContext] = {
    DelegatedAccountPrecondition.acceptOnlyOwnerRequest(mailboxSession, request.accountId)

    for {
      updateResults <- update(mailboxSession, request)
      currentStateIfAllUpdatesFailed <- retrieveCurrentStateIfAllUpdatesFailed(mailboxSession, updateResults)
      oldState <- evaluateOldState(updateResults, currentStateIfAllUpdatesFailed)
      newState <- evaluateNewState(updateResults, currentStateIfAllUpdatesFailed)
      response = createResponse(invocation.invocation, request, updateResults, oldState, newState)
    } yield InvocationWithContext(response, invocation.processingContext)
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
        case (_, Right(upsertRequest)) => updateSingletonSettingsObject(mailboxSession, upsertRequest)
        case (id, Left(e)) => SMono.just(SettingsUpdateFailure(id, e))
      }))
      .flatMap[SettingsUpdateResult](updateResultMono => updateResultMono)
      .map(updateResult => updateResult.asSettingsSetRequestUpdateResults)
      .reduce[SettingsSetUpdateResults](SettingsSetUpdateResults.empty())(SettingsSetUpdateResults.merge)

  private def updateSingletonSettingsObject(mailboxSession: MailboxSession, upsertRequest: JmapSettingsUpsertRequest): SMono[SettingsUpdateResult] =
    SMono.fromPublisher(jmapSettingsRepository.reset(mailboxSession.getUser, upsertRequest))
      .map(stateUpdate => SettingsUpdateSuccess(stateUpdate.oldState, stateUpdate.newState))

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