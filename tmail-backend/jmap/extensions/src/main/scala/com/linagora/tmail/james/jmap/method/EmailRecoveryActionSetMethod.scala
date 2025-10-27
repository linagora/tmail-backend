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

import java.time.temporal.ChronoUnit
import java.time.{Duration, ZonedDateTime}

import com.google.common.collect.ImmutableList
import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.linagora.tmail.james.jmap.json.EmailRecoveryActionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_MESSAGE_VAULT
import com.linagora.tmail.james.jmap.method.EmailRecoveryActionConfiguration.{DEFAULT_MAX_EMAIL_RECOVERY_PER_REQUEST, DEFAULT_RESTORATION_HORIZON}
import com.linagora.tmail.james.jmap.method.EmailRecoveryActionSetCreatePerformer.CreationResults
import com.linagora.tmail.james.jmap.model.{EmailRecoveryActionCreation, EmailRecoveryActionCreationId, EmailRecoveryActionCreationParseException, EmailRecoveryActionCreationRequest, EmailRecoveryActionCreationResponse, EmailRecoveryActionSetRequest, EmailRecoveryActionSetResponse, EmailRecoveryActionUpdateException, EmailRecoveryActionUpdatePatchObject, EmailRecoveryActionUpdateRequest, EmailRecoveryActionUpdateResponse, EmailRecoveryActionUpdateStatus, InvalidStatusUpdateException, UnparsedEmailRecoveryActionId}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{ClientId, Id, Invocation, Properties, ServerId, SetError}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodWithoutAccountId}
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.task.{TaskExecutionDetails, TaskId, TaskManager, TaskNotFoundException}
import org.apache.james.util.{DurationParser, ReactorUtils}
import org.apache.james.utils.PropertiesProvider
import org.apache.james.vault.search.{CriterionFactory, Query}
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTask.{AdditionalInformation => DeletedMessagesVaultRestoreTaskAdditionalInformation}
import org.apache.james.webadmin.vault.routes.{DeletedMessagesVaultRestoreTask, RestoreService}
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsObject}
import reactor.core.publisher.SynchronousSink
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.OptionConverters._
import scala.util.Try

class EmailRecoveryActionMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new MessageVaultCapabilitiesModule)
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[EmailRecoveryActionSetMethod])
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[EmailRecoveryActionGetMethod])
  }

  @Singleton
  @Provides
  def provideEmailRecoveryActionConfiguration(propertiesProvider: PropertiesProvider): EmailRecoveryActionConfiguration =
    EmailRecoveryActionConfiguration.from(propertiesProvider)
}

object EmailRecoveryActionConfiguration {

  val DEFAULT_MAX_EMAIL_RECOVERY_PER_REQUEST: Long = 5
  val DEFAULT_RESTORATION_HORIZON: Duration = DurationParser.parse("15", ChronoUnit.DAYS)

  def from(propertiesProvider: PropertiesProvider): EmailRecoveryActionConfiguration = {
    val config = Try(propertiesProvider.getConfiguration("jmap"))

    val maxEmailRecoveryPerRequest: Option[Long] = config
      .map(_.getLong("emailRecoveryAction.maxEmailRecoveryPerRequest"))
      .toOption
    val restorationHorizon: Option[Duration] = config
      .map(_.getString("emailRecoveryAction.restorationHorizon"))
      .map(DurationParser.parse)
      .toOption

    EmailRecoveryActionConfiguration(
      maxEmailRecoveryPerRequest = maxEmailRecoveryPerRequest.getOrElse(DEFAULT_MAX_EMAIL_RECOVERY_PER_REQUEST),
      restorationHorizon = restorationHorizon.getOrElse(DEFAULT_RESTORATION_HORIZON))
  }
}

case class EmailRecoveryActionConfiguration(maxEmailRecoveryPerRequest: Long, restorationHorizon: Duration) {
  def this() = {
    this(DEFAULT_MAX_EMAIL_RECOVERY_PER_REQUEST, DEFAULT_RESTORATION_HORIZON)
  }
}

class EmailRecoveryActionSetMethod @Inject()(val createPerformer: EmailRecoveryActionSetCreatePerformer,
                                             val updatePerformer: EmailRecoveryActionSetUpdatePerformer,
                                             val metricFactory: MetricFactory,
                                             val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[EmailRecoveryActionSetRequest] with Startable {


  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_MESSAGE_VAULT)

  override val methodName: MethodName = MethodName("EmailRecoveryAction/set")

  override def getRequest(invocation: Invocation): Either[Exception, EmailRecoveryActionSetRequest] =
    EmailRecoveryActionSerializer.deserializeSetRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailRecoveryActionSetRequest): Publisher[InvocationWithContext] = {
    for {
      createdResult <- createPerformer.create(request, mailboxSession.getUser)
      updatedResult <- updatePerformer.update(request, mailboxSession.getUser)
    } yield InvocationWithContext(
      invocation = Invocation(methodName, Arguments(
        EmailRecoveryActionSerializer.serializeSetResponse(
          EmailRecoveryActionSetResponse(
            created = createdResult.created.filter(_.nonEmpty),
            notCreated = createdResult.notCreated.filter(_.nonEmpty),
            updated = updatedResult.updated.filter(_.nonEmpty),
            notUpdated = updatedResult.notUpdated.filter(_.nonEmpty))
        )), invocation.invocation.methodCallId),
      processingContext = recordCreationIdInProcessingContext(createdResult, invocation.processingContext))
  }

  private def recordCreationIdInProcessingContext(results: CreationResults,
                                                  processingContext: ProcessingContext): ProcessingContext =
    results.created.getOrElse(Map())
      .foldLeft(processingContext)({
        case (processingContext, (creationId, result)) =>
          processingContext.recordCreatedId(ClientId(creationId.id), ServerId(Id.validate(result.id.asString()).toOption.get))
      })
}

object EmailRecoveryActionSetCreatePerformer {
  sealed trait CreationResult

  case class CreationSuccess(clientId: EmailRecoveryActionCreationId, response: EmailRecoveryActionCreationResponse) extends CreationResult

  case class CreationFailure(clientId: EmailRecoveryActionCreationId, exception: Exception) extends CreationResult {
    def asMessageSetError: SetError = exception match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e: EmailRecoveryActionCreationParseException => e.setError
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }

  case class CreationResults(results: Seq[CreationResult]) {
    def created: Option[Map[EmailRecoveryActionCreationId, EmailRecoveryActionCreationResponse]] =
      Option(results.flatMap {
        case result: CreationSuccess => Some((result.clientId, result.response))
        case _ => None
      }.toMap).filter(_.nonEmpty)

    def notCreated: Option[Map[EmailRecoveryActionCreationId, SetError]] =
      Option(results.flatMap {
        case failure: CreationFailure => Some((failure.clientId, failure.asMessageSetError))
        case _ => None
      }.toMap).filter(_.nonEmpty)
  }

}

class EmailRecoveryActionSetCreatePerformer @Inject()(val taskManager: TaskManager,
                                                      val restoreService: RestoreService,
                                                      val configuration: EmailRecoveryActionConfiguration) {

  import EmailRecoveryActionSetCreatePerformer._

  def create(request: EmailRecoveryActionSetRequest, userToRestore: Username): SMono[CreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map()))
      .concatMap {
        case (clientId, json) => parseCreateRequest(json)
          .fold(e => SMono.just[CreationResult](CreationFailure(clientId, e)),
            creationRequest => submitTask(clientId, userToRestore, creationRequest))
      }.collectSeq()
      .map(CreationResults)

  private def parseCreateRequest(json: JsObject): Either[Exception, EmailRecoveryActionCreationRequest] =
    for {
      validJsObject <- EmailRecoveryActionCreation.validateProperties(json)
      parsedRequest <- EmailRecoveryActionSerializer.deserializeSetCreationRequest(validJsObject).asEither
        .left.map(errors => new IllegalArgumentException(ResponseSerializer.serialize(JsError(errors)).toString))
    } yield parsedRequest

  private def submitTask(clientId: EmailRecoveryActionCreationId, userToRestore: Username, creationRequest: EmailRecoveryActionCreationRequest): SMono[CreationResult] = {
    val horizon: ZonedDateTime = ZonedDateTime.now().minus(configuration.restorationHorizon)
    val horizonCriterion = CriterionFactory.deletionDate().afterOrEquals(horizon)

    val modifiedQuery = new Query(ImmutableList.builder()
        .addAll(creationRequest.asQuery(configuration.maxEmailRecoveryPerRequest).getCriteria)
        .add(horizonCriterion)
        .build(),
      configuration.maxEmailRecoveryPerRequest)

    SMono.fromCallable(() => taskManager.submit(new DeletedMessagesVaultRestoreTask(restoreService, userToRestore, modifiedQuery)))
    .map(taskId => CreationSuccess(clientId, EmailRecoveryActionCreationResponse(taskId)))
    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
  }
}

object EmailRecoveryActionSetUpdatePerformer {
  private val STATUS_WHITE_LIST: Set[TaskManager.Status] = Set(TaskManager.Status.WAITING, TaskManager.Status.IN_PROGRESS)

  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[EmailRecoveryActionSetUpdatePerformer])

  sealed trait UpdateResult

  private case class UpdateSuccess(taskId: TaskId, response: EmailRecoveryActionUpdateResponse) extends UpdateResult

  private case class UpdateFailure(clientId: UnparsedEmailRecoveryActionId, exception: Throwable) extends UpdateResult {
    def asSetError: SetError = exception match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage), None)
      case e: EmailRecoveryActionUpdateException => e.setError.getOrElse(SetError.serverFail(SetErrorDescription(e.description.getOrElse(""))))
      case e: InvalidStatusUpdateException => SetError("invalidStatus", SetErrorDescription(e.getMessage), Some(Properties("status")))
      case _ =>
        LOGGER.warn("Could not taskUpdate email recovery action set method", exception)
        SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }

  case class UpdateResults(results: Seq[UpdateResult]) {
    def updated: Option[Map[TaskId, EmailRecoveryActionUpdateResponse]] =
      Option(results.flatMap {
        case result: UpdateSuccess => Some((result.taskId, result.response))
        case _ => None
      }.toMap).filter(_.nonEmpty)

    def notUpdated: Option[Map[UnparsedEmailRecoveryActionId, SetError]] =
      Option(results.flatMap {
        case failure: UpdateFailure => Some((failure.clientId, failure.asSetError))
        case _ => None
      }.toMap).filter(_.nonEmpty)
  }
}

class EmailRecoveryActionSetUpdatePerformer @Inject()(val taskManager: TaskManager) {

  import EmailRecoveryActionSetUpdatePerformer._

  def update(request: EmailRecoveryActionSetRequest, username: Username): SMono[UpdateResults] = {
    SFlux.fromIterable(request.update.getOrElse(Map()))
      .flatMap({
        case (unparsedId: UnparsedEmailRecoveryActionId, patch: EmailRecoveryActionUpdatePatchObject) =>
          val either: Either[IllegalArgumentException, SMono[UpdateResult]] = for {
            taskId <- unparsedId.asTaskId
            validatedPatch <- patch.asUpdateRequest
          } yield {
            doUpdate(username, taskId, validatedPatch)
          }
          either.fold(e => SMono.just(UpdateFailure(unparsedId, e)),
            sMono => sMono.onErrorResume(e => SMono.just(UpdateFailure(unparsedId, e))))
      }, maxConcurrency = ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(UpdateResults)
  }

  private def doUpdate(username: Username, taskId: TaskId, patch: EmailRecoveryActionUpdateRequest): SMono[UpdateResult] =
    getTaskDetail(username, taskId)
      .handle(validateTargetTaskStatus)
      .flatMap(_ => taskUpdate(taskId, patch))
      .`then`(SMono.just(UpdateSuccess(taskId, EmailRecoveryActionUpdateResponse())))
      .onErrorResume(e => SMono.just(UpdateFailure(UnparsedEmailRecoveryActionId.from(taskId), e)))

  private def taskUpdate(taskId: TaskId, patch: EmailRecoveryActionUpdateRequest): SMono[Unit] =
    patch.status.value match {
      case EmailRecoveryActionUpdateStatus.CANCELED => SMono.fromCallable(() => taskManager.cancel(taskId))
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      case _ => SMono.empty
    }

  private def getTaskDetail(username: Username, taskId: TaskId): SMono[TaskExecutionDetails] =
    SMono.fromCallable(() => taskManager.getExecutionDetails(taskId))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .onErrorResume {
        case _: TaskNotFoundException => SMono.empty
        case e => SMono.error(e)
      }
      .filter(taskDetail => taskDetail.getType.equals(DeletedMessagesVaultRestoreTask.TYPE))
      .map(taskDetail => taskDetail.getAdditionalInformation.toScala
        .map(additionalInformation => additionalInformation.asInstanceOf[DeletedMessagesVaultRestoreTaskAdditionalInformation])
        .filter(additionalInformation => additionalInformation.getUsername.equals(username.asString()))
        .map(_ => taskDetail))
      .flatMap(SMono.justOrEmpty)
      .switchIfEmpty(SMono.error(EmailRecoveryActionUpdateException(setError = Some(SetError.notFound(SetErrorDescription("Task not found"))))))

  private def validateTargetTaskStatus: (TaskExecutionDetails, SynchronousSink[AnyRef]) => Unit =
    (taskDetail: TaskExecutionDetails, sink: SynchronousSink[AnyRef]) => {
      if (!STATUS_WHITE_LIST.contains(taskDetail.getStatus)) {
        sink.error(InvalidStatusUpdateException(s"The task was in status `${taskDetail.getStatus.getValue}` and cannot be canceled"))
      } else {
        sink.next(taskDetail)
      }
    }
}
