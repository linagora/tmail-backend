package com.linagora.tmail.james.jmap.method

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.linagora.tmail.james.jmap.json.EmailRecoveryActionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_MESSAGE_VAULT
import com.linagora.tmail.james.jmap.method.EmailRecoveryActionSetCreatePerformer.CreationResults
import com.linagora.tmail.james.jmap.model.{EmailRecoveryActionCreation, EmailRecoveryActionCreationId, EmailRecoveryActionCreationParseException, EmailRecoveryActionCreationRequest, EmailRecoveryActionCreationResponse, EmailRecoveryActionSetRequest, EmailRecoveryActionSetResponse}
import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{ClientId, Id, Invocation, ServerId, SetError}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodWithoutAccountId}
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.task.TaskManager
import org.apache.james.util.ReactorUtils
import org.apache.james.utils.PropertiesProvider
import org.apache.james.webadmin.vault.routes.{DeletedMessagesVaultRestoreTask, RestoreService}
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject}
import reactor.core.scala.publisher.{SFlux, SMono}

import javax.inject.Inject
import scala.util.Try;

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
  def from(propertiesProvider: PropertiesProvider): EmailRecoveryActionConfiguration = {
    val maxEmailRecoveryPerRequest: Option[Long] = Try(propertiesProvider.getConfiguration("jmap"))
      .map(configuration => configuration.getLong("emailRecoveryAction.maxEmailRecoveryPerRequest"))
      .toOption
    EmailRecoveryActionConfiguration(maxEmailRecoveryPerRequest = maxEmailRecoveryPerRequest.getOrElse(DEFAULT_MAX_EMAIL_RECOVERY_PER_REQUEST))
  }
}

case class EmailRecoveryActionConfiguration(maxEmailRecoveryPerRequest: Long)

class EmailRecoveryActionSetMethod @Inject()(val createPerformer: EmailRecoveryActionSetCreatePerformer,
                                             val metricFactory: MetricFactory,
                                             val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[EmailRecoveryActionSetRequest] with Startable {


  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_MESSAGE_VAULT)

  override val methodName: MethodName = MethodName("EmailRecoveryAction/set")

  override def getRequest(invocation: Invocation): Either[Exception, EmailRecoveryActionSetRequest] =
    EmailRecoveryActionSerializer.deserializeSetRequest(invocation.arguments.value).asEither
      .left.map(errors => new IllegalArgumentException(ResponseSerializer.serialize(JsError(errors)).toString))

  override def doProcess(invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailRecoveryActionSetRequest): Publisher[InvocationWithContext] = {
    for {
      createdResult <- createPerformer.create(request, mailboxSession.getUser)
    } yield InvocationWithContext(
      invocation = Invocation(methodName, Arguments(
        EmailRecoveryActionSerializer.serializeSetResponse(
          EmailRecoveryActionSetResponse(
            created = createdResult.created.filter(_.nonEmpty),
            notCreated = createdResult.notCreated.filter(_.nonEmpty))
        )), invocation.invocation.methodCallId),
      processingContext = recordCreationIdInProcessingContext(createdResult, invocation.processingContext))
  }

  private def recordCreationIdInProcessingContext(results: CreationResults,
                                                  processingContext: ProcessingContext):ProcessingContext =
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

  private def submitTask(clientId: EmailRecoveryActionCreationId, userToRestore: Username, creationRequest: EmailRecoveryActionCreationRequest): SMono[CreationResult] =
    SMono.fromCallable(() => taskManager.submit(new DeletedMessagesVaultRestoreTask(restoreService, userToRestore,
      creationRequest.asQuery(configuration.maxEmailRecoveryPerRequest))))
    .map(taskId => CreationSuccess(clientId, EmailRecoveryActionCreationResponse(taskId)))
    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

}
