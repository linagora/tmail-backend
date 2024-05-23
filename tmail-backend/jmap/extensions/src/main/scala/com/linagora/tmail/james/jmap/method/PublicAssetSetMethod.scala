package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.PublicAssetSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_PUBLIC_ASSETS
import com.linagora.tmail.james.jmap.publicAsset.{ImageContentType, PublicAssetBlobIdNotFoundException, PublicAssetCreationFailure, PublicAssetCreationId, PublicAssetCreationParseException, PublicAssetCreationRequest, PublicAssetCreationResponse, PublicAssetCreationResult, PublicAssetCreationResults, PublicAssetCreationSuccess, PublicAssetException, PublicAssetInvalidBlobIdException, PublicAssetRepository, PublicAssetSetCreationRequest, PublicAssetSetRequest, PublicAssetSetResponse, PublicAssetSetService, PublicAssetStorage}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ClientId, Id, Invocation, ServerId, SessionTranslator, UuidState}
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.{Blob, BlobNotFoundException, BlobResolvers, ProcessingContext, SessionSupplier}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

class PublicAssetSetMethod @Inject()(val createPerformer: PublicAssetSetCreatePerformer,
                                     val metricFactory: MetricFactory,
                                     val sessionTranslator: SessionTranslator,
                                     val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[PublicAssetSetRequest] {

  override val methodName: Invocation.MethodName = MethodName("PublicAsset/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_PUBLIC_ASSETS)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: PublicAssetSetRequest): Publisher[InvocationWithContext] =
    for {
      creationResults <- createPerformer.createPublicAssets(mailboxSession, request)
      // Add updated, notUpdated, destroyed, notDestroyed here when they are implemented
    } yield {
      InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(
            PublicAssetSerializer.serializePublicAssetSetResponse(PublicAssetSetResponse(
              accountId = request.accountId,
              oldState = Some(UuidState.INSTANCE),
              newState = UuidState.INSTANCE,
              created = Some(creationResults.retrieveCreated).filter(_.nonEmpty),
              notCreated = Some(creationResults.retrieveErrors).filter(_.nonEmpty))).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId),
        processingContext = recordCreationIdInProcessingContext(creationResults, invocation.processingContext))
    }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, PublicAssetSetRequest] =
    PublicAssetSerializer.deserializePublicAssetSetRequest(invocation.arguments.value).asEitherRequest

  private def recordCreationIdInProcessingContext(results: PublicAssetCreationResults,
                                                  processingContext: ProcessingContext): ProcessingContext =
    results.retrieveCreated.foldLeft(processingContext)({
      case (processingContext, (creationId, result)) =>
        processingContext.recordCreatedId(ClientId(creationId.id), ServerId(Id.validate(result.id.asString()).toOption.get))
    })
}

object PublicAssetSetCreatePerformer {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[PublicAssetSetCreatePerformer])
}

class PublicAssetSetCreatePerformer @Inject()(val publicAssetRepository: PublicAssetRepository,
                                              val blobResolvers: BlobResolvers,
                                              val publicAssetSetService: PublicAssetSetService) {

  def createPublicAssets(mailboxSession: MailboxSession, request: PublicAssetSetRequest): SMono[PublicAssetCreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map.empty))
      .concatMap {
        case (publicAssetCreationId: PublicAssetCreationId, publicAssetCreationRequestAsJson: JsObject) => parseCreate(publicAssetCreationRequestAsJson)
          .fold(SMono.error(_), creationRequest => createPublicAssets(mailboxSession, creationRequest)
            .map(publicAsset => PublicAssetCreationSuccess(publicAssetCreationId, PublicAssetCreationResponse.from(publicAsset))))
          .onErrorResume(error => handleError(publicAssetCreationId, error))
      }.collectSeq()
      .map(PublicAssetCreationResults)

  private def handleError(publicAssetCreationId: PublicAssetCreationId, error: Throwable): SMono[PublicAssetCreationResult] =
    error match {
      case e: PublicAssetException => SMono.just(PublicAssetCreationFailure(publicAssetCreationId, e))
      case e: BlobNotFoundException => SMono.just(PublicAssetCreationFailure(publicAssetCreationId, e))
      case e => SMono.error(e)
    }

  private def createPublicAssets(mailboxSession: MailboxSession,
                                 creationRequest: PublicAssetSetCreationRequest): SMono[PublicAssetStorage] =
    generatePublicAssetCreationRequest(creationRequest, mailboxSession)
      .flatMap(publicAsset => SMono(publicAssetRepository.create(mailboxSession.getUser, publicAsset)))

  private def parseCreate(jsObject: JsObject): Either[PublicAssetCreationParseException, PublicAssetSetCreationRequest] =
    PublicAssetSetCreationRequest.validateProperties(jsObject)
      .flatMap(validJsObject => PublicAssetSerializer.deserializePublicAssetSetCreationRequest(validJsObject) match {
        case JsSuccess(creationRequest, _) => Right(creationRequest)
        case JsError(errors) => Left(PublicAssetCreationParseException(standardError(errors)))
      })

  private def parseIdentityIds(creationRequest: PublicAssetSetCreationRequest): SMono[Seq[IdentityId]] =
    SMono.fromCallable(() => creationRequest.parseIdentityIds)
      .flatMap(result => result.fold(e => SMono.error(e), ids => SMono.just(ids)))

  private def generatePublicAssetCreationRequest(creationRequest: PublicAssetSetCreationRequest,
                                                 session: MailboxSession): SMono[PublicAssetCreationRequest] =
    for {
      parsedIdentityIds <- parseIdentityIds(creationRequest)
      identityIds <- publicAssetSetService.checkIdentityIdsExist(parsedIdentityIds, session)
      blob <- resolveBlob(session, creationRequest)
      creationRequest <- SMono.fromTry(buildPublicAssetCreationRequest(blob, identityIds).toTry)
    } yield {
      creationRequest
    }

  private def buildPublicAssetCreationRequest(blob: Blob,
                                              identityIds: Seq[IdentityId]): Either[Throwable, PublicAssetCreationRequest] =
    for {
      assetContentType <- ImageContentType.from(blob.contentType)
      size <- blob.size.toEither
    } yield {
      PublicAssetCreationRequest(
        size = size,
        contentType = assetContentType,
        identityIds = identityIds,
        content = () => blob.content)
    }

  private def resolveBlob(session: MailboxSession, creationRequest: PublicAssetSetCreationRequest): SMono[Blob] =
    blobResolvers.resolve(creationRequest.blobId, session)
      .onErrorMap {
        case _: BlobNotFoundException => PublicAssetBlobIdNotFoundException(creationRequest.blobId.value.value)
        case _: IllegalArgumentException => PublicAssetInvalidBlobIdException(creationRequest.blobId.value.value)
      }
}