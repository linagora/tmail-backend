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

import java.util.Optional

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.json.FilterSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FILTER
import com.linagora.tmail.james.jmap.model.{Condition, FilterSetError, FilterSetRequest, FilterSetResponse, FilterSetUpdateResponse, FilterState, FilterTypeName, RuleWithId, Update}
import eu.timepit.refined.auto._
import jakarta.inject.{Inject, Named}
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
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MailboxId
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

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
  private val forbiddenFields: Set[String] = Set("flag", "internalDate", "savedDate")

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession,
                         request: FilterSetRequest): Publisher[InvocationWithContext] = {
    (for {
      oldState <- retrieveState(mailboxSession)
      updateResult <- update(mailboxSession, request)
      newState <- retrieveState(mailboxSession)
      response = createResponse(invocation.invocation, request, updateResult, oldState, newState)
    } yield {
      dispatchFilterChangeEvent(mailboxSession.getUser, oldState, newState)
        .`then`(SMono.just(InvocationWithContext(response, invocation.processingContext)))
    })
      .flatMap(publisher => publisher)
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, FilterSetRequest] =
    FilterSerializer(mailboxIdFactory).deserializeFilterSetRequest(invocation.arguments.value)
      .asEitherRequest

  private def dispatchFilterChangeEvent(username: Username, oldState: FilterState, newState: FilterState): SMono[Void] = {
    def noFilterChange: Boolean = oldState.equals(newState)

    if (noFilterChange) {
      SMono.empty
    } else {
      val filterChangedEvent = StateChangeEvent(eventId = EventId.random(),
        username = username,
        map = Map(FilterTypeName -> newState))

      SMono(eventBus.dispatch(filterChangedEvent, AccountIdRegistrationKey(AccountId.fromUsername(username))))
    }
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
    SFlux.fromIterable(filterSetRequest.parseUpdate())
      .flatMap[FilterSetUpdateResult](tuple =>
        tuple._2.flatMap(validateRules(_, mailboxSession))
          .map(update => SMono.fromCallable(() => RuleWithId.toJava(update.rules)))
          .fold(SMono.error, rules => rules.flatMap(rule => updateRules(mailboxSession.getUser, rule, filterSetRequest.ifInState)))
          .onErrorResume(e => SMono.just(FilterSetUpdateFailure(tuple._1, e))))
      .map(updateResult => updateResult.asFilterSetUpdateResults)
      .foldWith[FilterSetUpdateResults](FilterSetUpdateResults.empty())(FilterSetUpdateResults.merge)

  def updateRules(username: Username, validatedRules: List[Rule], ifInState: Option[FilterState]): SMono[FilterSetUpdateResult] =
      SMono(filteringManagement.defineRulesForUser(username, validatedRules.asJava, convertToOptionalVersion(ifInState)))
        .`then`(SMono.just(FilterSetUpdateSuccess))

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

  def validateRules(update: Update, mailboxSession: MailboxSession): Either[IllegalArgumentException, Update] = {
    if (!update.rules.distinctBy(_.id).length.equals(update.rules.length)) {
      return Left(new DuplicatedRuleException("There are some duplicated rules"))
    }

    update.rules.iterator.foreach { rule =>
      val matchedForbiddenField: Option[Condition] = rule.conditionGroup.conditions.find(condition => forbiddenFields.exists(_.equalsIgnoreCase(condition.field.string)))
      if (matchedForbiddenField.nonEmpty) {
        return Left(new IllegalArgumentException(s"Rules with field '${matchedForbiddenField.get.field.string}' are not supported"))
      }
      if (rule.action.forwardTo.exists(forward => forward.addresses.map(_.string).contains(mailboxSession.getUser.asString))) {
        return Left(new IllegalArgumentException("The mail address that are forwarded to could not be this mail address"))
      }
    }

    Right(update)
  }

  def convertToOptionalVersion(ifInState: Option[FilterState]): Optional[Version] =
    ifInState.map(filterState => FilterState.toVersion(filterState)).toJava

  def retrieveState(mailboxSession: MailboxSession): SMono[FilterState] =
    SMono(filteringManagement.getLatestVersion(mailboxSession.getUser))
      .map(version => FilterState(version.asInteger))

  class DuplicatedRuleException(message: String) extends IllegalArgumentException(message)
}