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

import cats.implicits.toTraverseOps
import com.google.common.base.Preconditions
import com.linagora.tmail.james.jmap.json.PublicAssetSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_PUBLIC_ASSETS
import com.linagora.tmail.james.jmap.publicAsset.{ImageContentType, PublicAssetBlobIdNotFoundException, PublicAssetCreationFailure, PublicAssetCreationId, PublicAssetCreationParseException, PublicAssetCreationRequest, PublicAssetCreationResponse, PublicAssetCreationResult, PublicAssetCreationResults, PublicAssetCreationSuccess, PublicAssetDeletionFailure, PublicAssetDeletionResult, PublicAssetDeletionResults, PublicAssetDeletionSuccess, PublicAssetException, PublicAssetId, PublicAssetInvalidBlobIdException, PublicAssetPatchObject, PublicAssetRepository, PublicAssetSetCreationRequest, PublicAssetSetRequest, PublicAssetSetResponse, PublicAssetSetService, PublicAssetStorage, PublicAssetUpdateFailure, PublicAssetUpdateResult, PublicAssetUpdateResults, PublicAssetUpdateSuccess, UnparsedPublicAssetId, ValidatedPublicAssetPatchObject}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{ClientId, Id, Invocation, Properties, ServerId, SessionTranslator, SetError, UuidState}
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.{Blob, BlobNotFoundException, BlobResolvers, ProcessingContext, SessionSupplier}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

object PublicAssetSetMethod {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[PublicAssetSetMethod])
}

class PublicAssetSetMethod @Inject()(val createPerformer: PublicAssetSetCreatePerformer,
                                     val updatePerformer: PublicAssetSetUpdatePerformer,
                                     val destroyPerformer: PublicAssetSetDestroyPerformer,
                                     val metricFactory: MetricFactory,
                                     val sessionTranslator: SessionTranslator,
                                     val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[PublicAssetSetRequest] {

  override val methodName: Invocation.MethodName = MethodName("PublicAsset/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_PUBLIC_ASSETS)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: PublicAssetSetRequest): Publisher[InvocationWithContext] =
    for {
      creationResults <- createPerformer.createPublicAssets(mailboxSession, request)
      updateResults <- updatePerformer.updatePublicAssets(mailboxSession, request)
      deletionResults <- destroyPerformer.deletePublicAssets(mailboxSession, request)
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
              notCreated = Some(creationResults.retrieveErrors).filter(_.nonEmpty),
              updated = Some(updateResults.updated).filter(_.nonEmpty),
              notUpdated = Some(updateResults.notUpdated).filter(_.nonEmpty),
              destroyed = Some(deletionResults.destroyed).filter(_.nonEmpty),
              notDestroyed = Some(deletionResults.retrieveErrors).filter(_.nonEmpty))).as[JsObject]),
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
      .flatMap(publicAsset => SMono(publicAssetSetService.create(mailboxSession.getUser, publicAsset)))

  private def parseCreate(jsObject: JsObject): Either[PublicAssetCreationParseException, PublicAssetSetCreationRequest] =
    PublicAssetSerializer.deserializePublicAssetSetCreationRequest(jsObject) match {
      case JsSuccess(creationRequest, _) => Right(creationRequest)
      case JsError(errors) => Left(PublicAssetCreationParseException(standardError(errors)))
    }


  private def doParseIdentityIds(creationRequest: PublicAssetSetCreationRequest): Either[PublicAssetCreationParseException, List[IdentityId]] =
    creationRequest.identityIds match {
      case None => Right(List.empty)
      case Some(identityIdMap) => identityIdMap.map {
        case (identityId: IdentityId, bolVal) => if (bolVal) {
          Right(identityId)
        } else {
          scala.Left(PublicAssetCreationParseException(SetError.invalidArguments(SetErrorDescription(s"identityId '$identityId' must be a true"), Some(Properties.toProperties(Set("identityIds"))))))
        }
      }.toList.sequence
  }

  private def parseIdentityIds(creationRequest: PublicAssetSetCreationRequest): SMono[Seq[IdentityId]] =
    SMono.fromCallable(() => doParseIdentityIds(creationRequest))
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


class PublicAssetSetUpdatePerformer @Inject()(val publicAssetRepository: PublicAssetRepository,
                                              val publicAssetSetService: PublicAssetSetService) {

  def updatePublicAssets(mailboxSession: MailboxSession, request: PublicAssetSetRequest): SMono[PublicAssetUpdateResults] =
    tryGetValidateUpdateRequest(request)
      .flatMap({
        case Left(updateFailure) => SMono.just(updateFailure)
        case Right((publicAssetId, validatedPath)) => updatePublicAsset(publicAssetId, validatedPath, mailboxSession)
      }, ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(PublicAssetUpdateResults)

  private def updatePublicAsset(publicAssetId: PublicAssetId, patch: ValidatedPublicAssetPatchObject, mailboxSession: MailboxSession): SMono[PublicAssetUpdateResult] =
    if (patch.isReset) {
      updatePublicAssetWhenResetIdentityIds(publicAssetId, patch, mailboxSession)
    } else {
      updatePublicAssetWhenPartialUpdate(publicAssetId, patch, mailboxSession)
    }

  private def updatePublicAssetWhenResetIdentityIds(publicAssetId: PublicAssetId, patch: ValidatedPublicAssetPatchObject, mailboxSession: MailboxSession): SMono[PublicAssetUpdateResult] = {
    Preconditions.checkArgument(patch.resetIdentityIds.isDefined, "resetIdentityIds must be defined".asInstanceOf[Object])
    publicAssetSetService.checkIdentityIdsExist(patch.resetIdentityIds.get, mailboxSession)
      .flatMap(checkedIdentityIds => SMono(publicAssetRepository.update(mailboxSession.getUser, publicAssetId, checkedIdentityIds.toSet)))
      .`then`(SMono.just[PublicAssetUpdateResult](PublicAssetUpdateSuccess(publicAssetId)))
      .onErrorResume(e => SMono.just(PublicAssetUpdateFailure(UnparsedPublicAssetId(publicAssetId.value.toString), e)))
  }

  private def updatePublicAssetWhenPartialUpdate(publicAssetId: PublicAssetId, patch: ValidatedPublicAssetPatchObject, mailboxSession: MailboxSession): SMono[PublicAssetUpdateResult] =
    publicAssetSetService.checkIdentityIdsExist(patch.identityIdsToAdd, mailboxSession)
      .flatMap(checkedIdentityIdsToAdd => SMono(publicAssetRepository.updateIdentityIds(mailboxSession.getUser, publicAssetId, checkedIdentityIdsToAdd, patch.identityIdsToRemove)))
      .`then`(SMono.just[PublicAssetUpdateResult](PublicAssetUpdateSuccess(publicAssetId)))
      .onErrorResume(e => SMono.just(PublicAssetUpdateFailure(UnparsedPublicAssetId(publicAssetId.value.toString), e)))

  private def tryGetValidateUpdateRequest(request: PublicAssetSetRequest): SFlux[Either[PublicAssetUpdateFailure, (PublicAssetId, ValidatedPublicAssetPatchObject)]] =
    SFlux.fromIterable(request.update.getOrElse(Map.empty))
      .map({
        case (unparsedPublicAssetId: UnparsedPublicAssetId, patch: PublicAssetPatchObject) =>
          (for {
            publicAssetId <- unparsedPublicAssetId.tryAsPublicAssetId
            validatedPath <- patch.validate
          } yield (publicAssetId, validatedPath))
            .left.map(e => PublicAssetUpdateFailure(unparsedPublicAssetId, e))
      })
}

class PublicAssetSetDestroyPerformer @Inject()(val publicAssetRepository: PublicAssetRepository) {

  def deletePublicAssets(mailboxSession: MailboxSession, request: PublicAssetSetRequest): SMono[PublicAssetDeletionResults] =
    SFlux.fromIterable(request.destroy.getOrElse(Seq()))
      .flatMap(unparsedId => deletePublicAsset(unparsedId, mailboxSession)
        .onErrorRecover(e => PublicAssetDeletionFailure(unparsedId, e)),
        maxConcurrency = ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(PublicAssetDeletionResults)

  private def deletePublicAsset(unparsedId: UnparsedPublicAssetId, mailboxSession: MailboxSession): SMono[PublicAssetDeletionResult] =
    unparsedId.tryAsPublicAssetId match {
      case Left(argumentException) => SMono.just(PublicAssetDeletionFailure(unparsedId, argumentException))
      case Right(publicAssetId) => SMono.fromPublisher(publicAssetRepository.remove(mailboxSession.getUser, publicAssetId))
        .`then`(SMono.just[PublicAssetDeletionResult](PublicAssetDeletionSuccess(publicAssetId)))
        .onErrorResume(e => SMono.just(PublicAssetDeletionFailure(unparsedId, e)))
    }
}