package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.tmail.james.jmap.json.LongLivedTokenSerializer
import com.linagora.tmail.james.jmap.longlivedtoken.{AuthenticationToken, LongLivedToken, LongLivedTokenId, LongLivedTokenSecret, LongLivedTokenStore, UnparsedLongLivedTokenId}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_LONG_LIVED_TOKEN
import com.linagora.tmail.james.jmap.model.{LongLivedTokenCreationId, LongLivedTokenCreationRequest, LongLivedTokenCreationRequestInvalidException, LongLivedTokenCreationResults, LongLivedTokenDestroyFailure, LongLivedTokenDestroyResult, LongLivedTokenDestroyResults, LongLivedTokenDestroySuccess, LongLivedTokenSetRequest, LongLivedTokenSetResponse, TokenCreationResult}
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, ClientId, Id, Invocation, ServerId, UrlPrefixes}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.{ProcessingContext, SessionSupplier}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers
import javax.inject.Inject

case object LongLivedTokenCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object LongLivedTokenCapability extends Capability {
  val properties: CapabilityProperties = LongLivedTokenCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_LONG_LIVED_TOKEN
}

class LongLivedTokenSetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[LongLivedTokenSetMethod])
  }

  @ProvidesIntoSet
  private def capability(): CapabilityFactory = LongLivedTokenCapabilityFactory
}

case object LongLivedTokenCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = LongLivedTokenCapability

  override def id(): CapabilityIdentifier = LINAGORA_LONG_LIVED_TOKEN
}

class LongLivedTokenSetMethod @Inject()(longLivedTokenStore: LongLivedTokenStore,
                                        val metricFactory: MetricFactory,
                                        val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[LongLivedTokenSetRequest] {

  override val methodName: Invocation.MethodName = MethodName("LongLivedToken/set")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_LONG_LIVED_TOKEN)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: LongLivedTokenSetRequest): Publisher[InvocationWithContext] = {
    for {
      created <- create(mailboxSession, request, invocation.processingContext)
      destroyed <- destroy(mailboxSession, request)
    } yield InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(LongLivedTokenSerializer.serializeLongLivedTokenSetResponse(LongLivedTokenSetResponse(
            accountId = request.accountId,
            created = created._1.created,
            notCreated = created._1.notCreated,
            destroyed = Some(destroyed.retrieveDestroyed).filter(_.nonEmpty),
            notDestroyed = Some(destroyed.retrieveNotDestroyed).filter(_.nonEmpty)
          )).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId),
        processingContext = created._2)

  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, LongLivedTokenSetRequest] =
    LongLivedTokenSerializer.deserializeLongLivedTokenSetRequest(invocation.arguments.value) match {
      case JsSuccess(setRequest, _) => Right(setRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def create(mailboxSession: MailboxSession,
                     longLivedTokenSetRequest: LongLivedTokenSetRequest,
                     processingContext: ProcessingContext): SMono[(LongLivedTokenCreationResults, ProcessingContext)] =
    SFlux.fromIterable(longLivedTokenSetRequest.create
        .getOrElse(Map.empty)
        .view)
      .fold[SMono[(LongLivedTokenCreationResults, ProcessingContext)]](SMono.just(LongLivedTokenCreationResults.empty -> processingContext)) {
        (acc: SMono[(LongLivedTokenCreationResults, ProcessingContext)], elem: (LongLivedTokenCreationId, JsObject)) => {
          val (emailSendCreationId, jsObject) = elem
          acc.flatMap {
            case (creationResult, processingContext) =>
              createEach(mailboxSession, emailSendCreationId, jsObject, processingContext)
                .map(createResult => LongLivedTokenCreationResults.merge(creationResult, createResult._1) -> createResult._2)
          }
        }
      }
      .flatMap(any => any)
      .subscribeOn(Schedulers.elastic())

  private def createEach(session: MailboxSession,
                         creationId: LongLivedTokenCreationId,
                         creationRequest: JsObject,
                         processingContext: ProcessingContext): SMono[(LongLivedTokenCreationResults, ProcessingContext)] =
    parseCreationRequest(creationRequest)
      .fold(error => SMono.error(error),
        createToken(session, _))
      .map(successResult =>
        recordCreationIdInProcessingContext(creationId, successResult.id, processingContext)
          .map(context => LongLivedTokenCreationResults.created(creationId, successResult) -> context)
          .fold(error => LongLivedTokenCreationResults.notCreated(creationId, error) -> processingContext,
            createResultWithUpdatedContext => createResultWithUpdatedContext
          ))
      .onErrorResume(error => SMono.just(LongLivedTokenCreationResults.notCreated(creationId, error) -> processingContext))

  private def createToken(session: MailboxSession,
                          longLivedTokenCreationRequest: LongLivedTokenCreationRequest): SMono[TokenCreationResult] =
    SMono.fromCallable(() => LongLivedTokenSecret.generate)
      .flatMap(secretKey => SMono.fromPublisher(longLivedTokenStore.store(session.getUser, LongLivedToken(longLivedTokenCreationRequest.deviceId, secretKey)))
        .map(longLivedTokenId => TokenCreationResult(longLivedTokenId, AuthenticationToken(session.getUser, secretKey))))

  private def parseCreationRequest(jsObject: JsObject): Either[LongLivedTokenCreationRequestInvalidException, LongLivedTokenCreationRequest] =
    LongLivedTokenSerializer.deserializeLongLivedTokenCreationRequest(jsObject) match {
      case JsSuccess(createRequest, _) => createRequest.validate
      case JsError(errors) => Left(LongLivedTokenCreationRequestInvalidException.parse(errors))
    }

  private def recordCreationIdInProcessingContext(longLivedTokenCreationId: LongLivedTokenCreationId,
                                                  longLivedTokenId: LongLivedTokenId,
                                                  processingContext: ProcessingContext): Either[Exception, ProcessingContext] =
    Id.validate(longLivedTokenId.value.toString)
      .map(serverId => processingContext.recordCreatedId(ClientId(longLivedTokenCreationId.id), ServerId(serverId)))

  private def destroy(mailboxSession: MailboxSession,
                      longLivedTokenSetRequest: LongLivedTokenSetRequest): SMono[LongLivedTokenDestroyResults] =
    SFlux.fromIterable(longLivedTokenSetRequest.destroy.getOrElse(Seq()))
      .flatMap(id => delete(mailboxSession, id)
        .onErrorRecover(e => LongLivedTokenDestroyFailure(id, e)))
      .collectSeq()
      .map(results => LongLivedTokenDestroyResults(results))

  private def delete(mailboxSession: MailboxSession,
                     id: UnparsedLongLivedTokenId): SMono[LongLivedTokenDestroyResult] =
    LongLivedTokenId.parse(id.value)
      .fold(e => SMono.error(e),
        id => SMono.fromPublisher(longLivedTokenStore.revoke(mailboxSession.getUser, id))
          .subscribeOn(Schedulers.elastic())
          .`then`(SMono.just[LongLivedTokenDestroyResult](LongLivedTokenDestroySuccess(id))))
}
