package com.linagora.tmail.james.jmap.method

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.json.EmailRecoveryActionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_MESSAGE_VAULT
import com.linagora.tmail.james.jmap.model.{EmailRecoveryAction, EmailRecoveryActionGetRequest, EmailRecoveryActionGetResponse, EmailRecoveryActionIds, ErrorRestoreCount, SuccessfulRestoreCount, TaskIdUtil}
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, UnsignedInt}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, MethodWithoutAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.task.{TaskExecutionDetails, TaskId, TaskManager, TaskNotFoundException}
import org.apache.james.util.ReactorUtils
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTask
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsonValidationError}
import reactor.core.scala.publisher.{SFlux, SMono}

class EmailRecoveryActionGetMethod @Inject()()(val taskManager: TaskManager,
                                               val metricFactory: MetricFactory,
                                               val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[EmailRecoveryActionGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("EmailRecoveryAction/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_MESSAGE_VAULT)

  override def getRequest(invocation: Invocation): Either[Exception, EmailRecoveryActionGetRequest] =
    EmailRecoveryActionSerializer.deserializeGetRequest(invocation.arguments.value).asEither
      .left.map(errors => errors.head match {
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) =>
        new IllegalArgumentException(s"Missing '$path' property")
      case (path, Seq(JsonValidationError(Seq("error.expected.jsarray")))) =>
        new IllegalArgumentException(s"'$path' property need to be an array")
      case _ => new IllegalArgumentException(ResponseSerializer.serialize(JsError(errors)).toString)
    })

  override def doProcess(invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailRecoveryActionGetRequest): Publisher[InvocationWithContext] =
    request.validateProperties
      .fold(e => SMono.error(e),
        properties => retrieveEmailRecoveryActions(mailboxSession.getUser, request.ids)
          .collectSeq()
          .map(emailRecoveryActions => EmailRecoveryActionGetResponse.from(emailRecoveryActions, request.ids))
          .map(response => Invocation(
            methodName = methodName,
            arguments = Arguments(EmailRecoveryActionSerializer.serializeGetResponse(response, properties).as[JsObject]),
            methodCallId = invocation.invocation.methodCallId))
          .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext)))

  private def retrieveEmailRecoveryActions(username: Username, ids: EmailRecoveryActionIds): SFlux[EmailRecoveryAction] =
    SFlux.fromIterable(filteringValidTaskIds(ids))
      .flatMap(taskId => SMono.fromCallable(() => taskManager.getExecutionDetails(taskId))
        .onErrorResume {
          case _: TaskNotFoundException => SMono.empty
          case e => SMono.error(e)
        }
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
      .filter(taskDetail => isDeletedMessagesVaultRestoreTask(taskDetail) && belongsToUser(username, taskDetail))
      .map(toEmailRecoveryAction)

  private def filteringValidTaskIds(ids: EmailRecoveryActionIds): List[TaskId] =
    ids.list.map(unparsedId => TaskIdUtil.liftOrThrow(unparsedId).toOption)
      .filter(_.isDefined)
      .map(_.get)

  private def isDeletedMessagesVaultRestoreTask(taskDetail: TaskExecutionDetails): Boolean =
    taskDetail.getType.equals(DeletedMessagesVaultRestoreTask.TYPE)

  private def belongsToUser(username: Username, taskDetail: TaskExecutionDetails): Boolean =
    taskDetail.getAdditionalInformation.get()
      .asInstanceOf[DeletedMessagesVaultRestoreTask.AdditionalInformation]
      .getUsername
      .equals(username.asString())

  private def toEmailRecoveryAction(taskDetail: TaskExecutionDetails): EmailRecoveryAction = {
      val additionalInformation: DeletedMessagesVaultRestoreTask.AdditionalInformation = taskDetail.getAdditionalInformation.get()
        .asInstanceOf[DeletedMessagesVaultRestoreTask.AdditionalInformation]

      EmailRecoveryAction(id = taskDetail.getTaskId,
        successfulRestoreCount = SuccessfulRestoreCount(UnsignedInt.liftOrThrow(additionalInformation.getSuccessfulRestoreCount)),
        errorRestoreCount = ErrorRestoreCount(UnsignedInt.liftOrThrow(additionalInformation.getErrorRestoreCount)),
        status = taskDetail.getStatus)
    }
}
