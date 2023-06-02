package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.json.FilterSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FILTER
import com.linagora.tmail.james.jmap.model.{FilterSetError, FilterSetRequest, FilterSetResponse, FilterSetUpdateResponse, FilterState, FilterTypeName, RuleWithId}
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.events.EventBus
import org.apache.james.jmap.InjectionKeys
import org.apache.james.jmap.api.exception.StateMismatchException
import org.apache.james.jmap.api.filtering.{FilteringManagement, Rule, Version}
import org.apache.james.jmap.api.model.{AccountId, TypeName}
import org.apache.james.jmap.change.{AccountIdRegistrationKey, StateChangeEvent}
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Invocation, SessionTranslator}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MailboxId
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

import java.util.Optional
import javax.inject.{Inject, Named}
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._


class FilterSetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[FilterSetMethod])
    Multibinder.newSetBinder(binder(), classOf[TypeName])
      .addBinding()
      .toInstance(FilterTypeName)
  }
}

object FilterSetUpdateResults {
  def empty(): FilterSetUpdateResults = FilterSetUpdateResults(Map(), Map())

  def merge(a: FilterSetUpdateResults, b: FilterSetUpdateResults): FilterSetUpdateResults = FilterSetUpdateResults(a.updateSuccess ++ b.updateSuccess, a.updateFailures ++ b.updateFailures)
}

case class FilterSetUpdateResults(updateSuccess: Map[String, FilterSetUpdateResponse],
                                  updateFailures: Map[String, FilterSetError])
sealed trait FilterSetUpdateResult {
  def updated: Map[String, FilterSetUpdateResponse]
  def notUpdated: Map[String, FilterSetError]

  def asFilterSetUpdateResults: FilterSetUpdateResults = FilterSetUpdateResults(updated, notUpdated)
}

case object FilterSetUpdateSuccess extends FilterSetUpdateResult {
  override def updated: Map[String, FilterSetUpdateResponse] = Map("singleton" -> FilterSetUpdateResponse(JsObject(Seq())))

  override def notUpdated: Map[String, FilterSetError] = Map()
}

case class FilterSetUpdateFailure(id: String, exception: Throwable) extends FilterSetUpdateResult {
  override def updated: Map[String, FilterSetUpdateResponse] = Map()

  override def notUpdated: Map[String, FilterSetError] = Map(id -> asSetError(exception))

  def asSetError(exception: Throwable): FilterSetError = exception match {
    case e: IllegalArgumentException => FilterSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)))
    case e: StateMismatchException => FilterSetError.stateMismatch(Some(SetErrorDescription(e.getMessage)))
    case e: Throwable => FilterSetError.serverFail(Some(SetErrorDescription(e.getMessage)))
  }
}


class FilterSetMethod @Inject()(@Named(InjectionKeys.JMAP) eventBus: EventBus,
                                val metricFactory: MetricFactory,
                                val sessionSupplier: SessionSupplier,
                                val sessionTranslator: SessionTranslator,
                                val mailboxIdFactory: MailboxId.Factory,
                                filteringManagement: FilteringManagement) extends MethodRequiringAccountId[FilterSetRequest] {

  override val methodName: Invocation.MethodName = MethodName("Filter/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(LINAGORA_FILTER)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession,
                         request: FilterSetRequest): Publisher[InvocationWithContext] = {
    for {
      oldState <- retrieveState(mailboxSession)
      updateResult <- update(mailboxSession, request)
      newState <- retrieveState(mailboxSession)
      response = createResponse(invocation.invocation, request, updateResult, oldState, newState)
    } yield {
      val event = StateChangeEvent(eventId = EventId.random(),
        username = mailboxSession.getUser,
        map = Map(FilterTypeName -> newState))
      val accountId = AccountId.fromUsername(mailboxSession.getUser)
      SMono(eventBus.dispatch(event, AccountIdRegistrationKey(accountId))).subscribe()
      InvocationWithContext(response, invocation.processingContext)
    }
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, FilterSetRequest] =
    FilterSerializer(mailboxIdFactory).deserializeFilterSetRequest(invocation.arguments.value) match {
      case JsSuccess(filterSetRequest, _) => Right(filterSetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  def createResponse(invocation: Invocation,
                     filterSetRequest: FilterSetRequest,
                     updateResult: FilterSetUpdateResults,
                     oldState: FilterState,
                     newState: FilterState): Invocation = {
    val response = FilterSetResponse(
      accountId = filterSetRequest.accountId,
      oldState = Some(oldState),
      newState = newState,
      updated = Some(updateResult.updateSuccess).filter(_.nonEmpty),
      notUpdated = Some(updateResult.updateFailures).filter(_.nonEmpty),
      notCreated = validateNoCreate(filterSetRequest),
      notDestroyed = validateNoDestroy(filterSetRequest))

    Invocation(methodName,
      Arguments(FilterSerializer(mailboxIdFactory).serializeFilterSetResponse(response).as[JsObject]),
      invocation.methodCallId)
  }

  def update(mailboxSession: MailboxSession, filterSetRequest: FilterSetRequest): SMono[FilterSetUpdateResults] =
    SFlux.fromIterable(
      filterSetRequest.parseUpdate()
        .map[SMono[FilterSetUpdateResult]]({
          case (id, Right(update)) => {
            validateRules(update.rules)
              .fold[SMono[FilterSetUpdateResult]](
                e => SMono.just(FilterSetUpdateFailure(id, e)),
                _ => SMono.fromCallable(() => RuleWithId.toJava(update.rules))
                  .flatMap(rule => updateRules(mailboxSession.getUser, rule, filterSetRequest.ifInState)))
          }
            .onErrorResume(e => {
                SMono.just(FilterSetUpdateFailure(id, e))
            })
          case (id, Left(e)) => SMono.just(FilterSetUpdateFailure(id, e))
        }))
      .flatMap[FilterSetUpdateResult](updateResultMono => updateResultMono)
      .map(updateResult => updateResult.asFilterSetUpdateResults)
      .foldWith[FilterSetUpdateResults](FilterSetUpdateResults.empty())(FilterSetUpdateResults.merge)

  def updateRules(username: Username, validatedRules: List[Rule], ifInState: Option[FilterState]): SMono[FilterSetUpdateResult] = {
      SMono(filteringManagement.defineRulesForUser(username, validatedRules.asJava, convertToOptionalVersion(ifInState)))
        .`then`(SMono.just(FilterSetUpdateSuccess))
  }

  def validateNoCreate(filterSetRequest: FilterSetRequest): Option[Map[String, FilterSetError]] =
    filterSetRequest.create.map(aMap => aMap
      .view
      .mapValues(_ => FilterSetError.invalidArgument(
        Some(SetErrorDescription("'create' is not supported on singleton objects"))))
      .toMap)

  def validateNoDestroy(filterSetRequest: FilterSetRequest): Option[Map[String, FilterSetError]] =
    filterSetRequest.destroy.map(aSet =>
      aSet.map(id => (id, FilterSetError.invalidArgument(
        Some(SetErrorDescription("'destroy' is not supported on singleton objects")))))
        .toMap)

  def validateRules(rules: List[RuleWithId]): Either[IllegalArgumentException, Unit] = {
    if (!rules.distinctBy(_.id).length.equals(rules.length)) {
      Left(new DuplicatedRuleException("There are some duplicated rules"))
    } else {
      Right()
    }
  }

  def convertToOptionalVersion(ifInState: Option[FilterState]): Optional[Version] =
    ifInState.map(filterState => FilterState.toVersion(filterState)).toJava

  def retrieveState(mailboxSession: MailboxSession): SMono[FilterState] =
    SMono(filteringManagement.getLatestVersion(mailboxSession.getUser))
      .map(version => FilterState(version.asInteger))

  class DuplicatedRuleException(message: String) extends IllegalArgumentException(message)
  class MultipleMailboxIdException(message: String) extends IllegalArgumentException(message)
}