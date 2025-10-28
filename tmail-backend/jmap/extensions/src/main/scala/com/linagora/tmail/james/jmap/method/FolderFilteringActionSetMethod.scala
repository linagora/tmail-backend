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

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.json.FolderFilteringActionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FILTER
import com.linagora.tmail.james.jmap.model._
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.api.filtering.FilteringManagement
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_SHARES, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{ClientId, Id, Invocation, Properties, ServerId, SetError}
import org.apache.james.jmap.mail.MailboxGet
import org.apache.james.jmap.method.MailboxSetMethod.assertCapabilityIfSharedMailbox
import org.apache.james.jmap.method.{InvocationWithContext, MethodWithoutAccountId}
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.MailboxId
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.task.{TaskExecutionDetails, TaskId, TaskManager, TaskNotFoundException}
import org.apache.james.util.ReactorUtils
import org.apache.james.webadmin.data.jmap.{RunRulesOnMailboxService, RunRulesOnMailboxTask}
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsObject
import reactor.core.publisher.SynchronousSink
import reactor.core.scala.publisher.{SFlux, SMono}

class FolderFilteringActionSetMethod @Inject()(val createPerformer: FolderFilteringActionSetCreatePerformer,
                                               val updatePerformer: FolderFilteringActionSetUpdatePerformer,
                                               val metricFactory: MetricFactory,
                                               val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[FolderFilteringActionSetRequest] with Startable {
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_FILTER)
  override val methodName: MethodName = MethodName("FolderFilteringAction/set")

  override def getRequest(invocation: Invocation): Either[Exception, FolderFilteringActionSetRequest] =
    FolderFilteringActionSerializer.deserializeSetRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: FolderFilteringActionSetRequest): Publisher[InvocationWithContext] =
    for {
      createdResult <- createPerformer.create(request, mailboxSession, capabilities.contains(JAMES_SHARES))
      updatedResult <- updatePerformer.update(request, mailboxSession.getUser)
    } yield InvocationWithContext(
      invocation = Invocation(methodName, Arguments(
        FolderFilteringActionSerializer.serializeSetResponse(
          FolderFilteringActionSetResponse(
            created = createdResult.created.filter(_.nonEmpty),
            notCreated = createdResult.notCreated.filter(_.nonEmpty),
            updated = updatedResult.updated.filter(_.nonEmpty),
            notUpdated = updatedResult.notUpdated.filter(_.nonEmpty))
        )), invocation.invocation.methodCallId),
      processingContext = recordCreationIdInProcessingContext(createdResult, invocation.processingContext))

  private def recordCreationIdInProcessingContext(results: FolderFilteringActionSetCreatePerformer.CreationResults,
                                                  processingContext: ProcessingContext): ProcessingContext =
    results.created.getOrElse(Map())
      .foldLeft(processingContext)({
        case (ctx, (creationId, result)) =>
          ctx.recordCreatedId(ClientId(creationId.id), ServerId(Id.validate(result.id.asString()).toOption.get))
      })
}

object FolderFilteringActionSetCreatePerformer {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[FolderFilteringActionSetCreatePerformer])

  sealed trait CreationResult
  case class CreationSuccess(clientId: FolderFilteringActionCreationId, response: FolderFilteringActionCreationResponse) extends CreationResult
  case class CreationFailure(clientId: FolderFilteringActionCreationId, exception: Throwable) extends CreationResult {
    def asSetError: SetError = exception match {
      case e: FolderFilteringActionCreationParseException => e.setError
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"Invalid mailboxId: ${e.getMessage}"))
      case e: MailboxNotFoundException => SetError.notFound(SetErrorDescription(e.getMessage))
      case _ =>
        LOGGER.error("Failed create folder filtering action", exception)
        SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }

  case class CreationResults(results: Seq[CreationResult]) {
    def created: Option[Map[FolderFilteringActionCreationId, FolderFilteringActionCreationResponse]] =
      Option(results.flatMap {
        case s: CreationSuccess => Some((s.clientId, s.response))
        case _ => None
      }.toMap).filter(_.nonEmpty)

    def notCreated: Option[Map[FolderFilteringActionCreationId, SetError]] =
      Option(results.flatMap {
        case f: CreationFailure => Some((f.clientId, f.asSetError))
        case _ => None
      }.toMap).filter(_.nonEmpty)
  }
}

class FolderFilteringActionSetCreatePerformer @Inject()(taskManager: TaskManager,
                                                        mailboxManager: MailboxManager,
                                                        mailboxIdFactory: MailboxId.Factory,
                                                        filteringManagement: FilteringManagement,
                                                        runRulesService: RunRulesOnMailboxService) {
  import FolderFilteringActionSetCreatePerformer._

  def create(request: FolderFilteringActionSetRequest, mailboxSession: MailboxSession, supportSharedMailbox: Boolean): SMono[CreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map()))
      .concatMap {
        case (clientId, json) => parseCreateRequest(json)
          .fold(e => SMono.just[CreationResult](CreationFailure(clientId, e)),
            creation => submitTask(clientId, mailboxSession, creation, supportSharedMailbox))
      }
      .collectSeq()
      .map(CreationResults)

  private def parseCreateRequest(json: JsObject): Either[Exception, FolderFilteringActionCreationRequest] =
    for {
      validatedJsObject <- FolderFilteringActionCreation.validateProperties(json)
      parsed <- FolderFilteringActionSerializer.deserializeSetCreationRequest(validatedJsObject).asEitherRequest
    } yield parsed

  private def submitTask(clientId: FolderFilteringActionCreationId, mailboxSession: MailboxSession, creation: FolderFilteringActionCreationRequest, supportSharedMailbox: Boolean): SMono[CreationResult] =
    MailboxGet.parse(mailboxIdFactory)(creation.mailboxId)
      .fold(
        (e: Throwable) => SMono.just[CreationResult](CreationFailure(clientId, e)),
        (parsedMailboxId: MailboxId) =>
          SMono(mailboxManager.getMailboxReactive(parsedMailboxId, mailboxSession))
            .filterWhen(assertCapabilityIfSharedMailbox(mailboxSession, parsedMailboxId, supportSharedMailbox))
            .flatMap(messageManager => SMono.fromPublisher(filteringManagement.listRulesForUser(mailboxSession.getUser))
              .flatMap(rules => SMono.fromCallable(() => taskManager.submit(new RunRulesOnMailboxTask(mailboxSession.getUser, messageManager.getMailboxPath, rules, runRulesService)))
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
              .map(taskId => CreationSuccess(clientId, FolderFilteringActionCreationResponse(taskId))))
            .onErrorResume(e => SMono.just[CreationResult](CreationFailure(clientId, e))))
}

object FolderFilteringActionSetUpdatePerformer {
  private val STATUS_WHITE_LIST: Set[TaskManager.Status] = Set(TaskManager.Status.WAITING, TaskManager.Status.IN_PROGRESS)
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[FolderFilteringActionSetUpdatePerformer])

  sealed trait UpdateResult
  private case class UpdateSuccess(taskId: TaskId, response: FolderFilteringActionUpdateResponse) extends UpdateResult
  private case class UpdateFailure(clientId: UnparsedFolderFilteringActionId, exception: Throwable) extends UpdateResult {
    def asSetError: SetError = exception match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage), None)
      case e: FolderFilteringActionNotFoundException => SetError.notFound(SetErrorDescription(e.getMessage))
      case e: InvalidStatusUpdateException => SetError("invalidStatus", SetErrorDescription(e.getMessage), Some(Properties("status")))
      case _ =>
        LOGGER.error("Could not update folder filtering action", exception)
        SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }

  case class UpdateResults(results: Seq[UpdateResult]) {
    def updated: Option[Map[TaskId, FolderFilteringActionUpdateResponse]] =
      Option(results.flatMap {
        case result: UpdateSuccess => Some((result.taskId, result.response))
        case _ => None
      }.toMap).filter(_.nonEmpty)

    def notUpdated: Option[Map[UnparsedFolderFilteringActionId, SetError]] =
      Option(results.flatMap {
        case failure: UpdateFailure => Some((failure.clientId, failure.asSetError))
        case _ => None
      }.toMap).filter(_.nonEmpty)
  }
}

class FolderFilteringActionSetUpdatePerformer @Inject()(val taskManager: TaskManager) {
  import FolderFilteringActionSetUpdatePerformer._

  def update(request: FolderFilteringActionSetRequest, username: Username): SMono[UpdateResults] =
    SFlux.fromIterable(request.update.getOrElse(Map()))
      .flatMap({
        case (unparsedId: UnparsedFolderFilteringActionId, patch: FolderFilteringActionUpdatePatchObject) =>
          val either: Either[IllegalArgumentException, SMono[UpdateResult]] = for {
            taskId <- unparsedId.asTaskId
            validatedPatch <- patch.asUpdateRequest
          } yield {
            doUpdate(username, taskId, validatedPatch)
          }
          either.fold(e => SMono.just(UpdateFailure(unparsedId, e)),
            updateMono => updateMono.onErrorResume(e => SMono.just(UpdateFailure(unparsedId, e))))
      }, maxConcurrency = ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(UpdateResults)

  private def doUpdate(username: Username, taskId: TaskId, patch: FolderFilteringActionUpdateRequest): SMono[UpdateResult] =
    getTaskDetail(username, taskId)
      .handle(validateTargetTaskStatus)
      .flatMap(_ => cancelTask(taskId, patch))
      .`then`(SMono.just(UpdateSuccess(taskId, FolderFilteringActionUpdateResponse())))
      .onErrorResume(e => SMono.just(UpdateFailure(UnparsedFolderFilteringActionId.from(taskId), e)))

  private def getTaskDetail(username: Username, taskId: TaskId): SMono[TaskExecutionDetails] =
    SMono.fromCallable(() => taskManager.getExecutionDetails(taskId))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .onErrorResume {
        case _: TaskNotFoundException => SMono.empty
        case e => SMono.error(e)
      }
      .filter(taskDetail => isRunRulesOnMailboxTask(taskDetail) && belongsToUser(username, taskDetail))
      .switchIfEmpty(SMono.error(FolderFilteringActionNotFoundException(message = s"Task ${taskId.asString()} not found")))

  private def isRunRulesOnMailboxTask(taskDetail: TaskExecutionDetails): Boolean =
    taskDetail.getType.equals(RunRulesOnMailboxTask.TASK_TYPE)

  private def belongsToUser(username: Username, taskDetail: TaskExecutionDetails): Boolean =
    taskDetail.getAdditionalInformation.get()
      .asInstanceOf[RunRulesOnMailboxTask.AdditionalInformation]
      .getUsername
      .equals(username)

  private def validateTargetTaskStatus: (TaskExecutionDetails, SynchronousSink[AnyRef]) => Unit =
    (taskDetail: TaskExecutionDetails, sink: SynchronousSink[AnyRef]) => {
      if (!STATUS_WHITE_LIST.contains(taskDetail.getStatus)) {
        sink.error(InvalidStatusUpdateException(s"The task was in status `${taskDetail.getStatus.getValue}` and cannot be canceled"))
      } else {
        sink.next(taskDetail)
      }
    }

  private def cancelTask(taskId: TaskId, patch: FolderFilteringActionUpdateRequest): SMono[Unit] =
    patch.status.value match {
      case FolderFilteringActionUpdateStatus.CANCELED => SMono.fromCallable(() => taskManager.cancel(taskId))
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      case _ => SMono.empty
    }
}
