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

import com.linagora.tmail.james.jmap.blob.UnauthenticatedBlobDownloadTokenRepository
import com.linagora.tmail.james.jmap.json.UnauthenticatedBlobAccessSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_UNAUTHENTICATED_BLOB_ACCESS
import com.linagora.tmail.james.jmap.model.{UnauthenticatedBlobAccessCreationResponse, UnauthenticatedBlobAccessSetRequest, UnauthenticatedBlobAccessSetResponse}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.blob.api.{BlobId => BlobStoreBlobId}
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Invocation, JmapRfc8621Configuration, SessionTranslator, SetError}
import org.apache.james.jmap.mail.{BlobId => JmapBlobId}
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.{BlobNotFoundException, BlobResolvers, SessionSupplier}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsObject, JsValue}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.{Failure, Success, Try}

object UnauthenticatedBlobAccessSetMethod {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[UnauthenticatedBlobAccessSetMethod])
}

class UnauthenticatedBlobAccessSetMethod @Inject()(val createPerformer: UnauthenticatedBlobAccessSetCreatePerformer,
                                                   val metricFactory: MetricFactory,
                                                   val sessionTranslator: SessionTranslator,
                                                   val sessionSupplier: SessionSupplier,
                                                   val configuration: JmapRfc8621Configuration) extends MethodRequiringAccountId[UnauthenticatedBlobAccessSetRequest] {

  override val methodName: MethodName = MethodName("UnauthenticatedBlobAccess/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_UNAUTHENTICATED_BLOB_ACCESS)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, UnauthenticatedBlobAccessSetRequest] =
    for {
      request <- UnauthenticatedBlobAccessSerializer.deserializeSetRequest(invocation.arguments.value).asEitherRequest
      _ <- request.validate(configuration)
    } yield request

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: UnauthenticatedBlobAccessSetRequest): Publisher[InvocationWithContext] =
    createPerformer.create(mailboxSession, request)
      .map(results => InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(UnauthenticatedBlobAccessSerializer.serializeSetResponse(UnauthenticatedBlobAccessSetResponse(
            accountId = request.accountId,
            created = Some(results.created).filter(_.nonEmpty),
            notCreated = Some(results.notCreated).filter(_.nonEmpty),
            notUpdated = unsupportedUpdates(request),
            notDestroyed = unsupportedDestroy(request))).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId),
        processingContext = invocation.processingContext))

  private def unsupportedUpdates(request: UnauthenticatedBlobAccessSetRequest): Option[Map[String, SetError]] =
    request.update
      .map(_.keys.map(id => id -> unsupportedOperationError("update")).toMap)
      .filter(_.nonEmpty)

  private def unsupportedDestroy(request: UnauthenticatedBlobAccessSetRequest): Option[Map[String, SetError]] =
    request.destroy
      .map(_.map(id => id -> unsupportedOperationError("destroy")).toMap)
      .filter(_.nonEmpty)

  private def unsupportedOperationError(operation: String): SetError =
    SetError.invalidArguments(SetErrorDescription(s"`$operation` is not supported by UnauthenticatedBlobAccess/set"))
}

class UnauthenticatedBlobAccessSetCreatePerformer @Inject()(val tokenRepository: UnauthenticatedBlobDownloadTokenRepository,
                                                            val blobResolvers: BlobResolvers,
                                                            val blobIdFactory: BlobStoreBlobId.Factory) {
  import UnauthenticatedBlobAccessSetMethod.LOGGER

  def create(mailboxSession: MailboxSession, request: UnauthenticatedBlobAccessSetRequest): SMono[UnauthenticatedBlobAccessCreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map.empty))
      .flatMap({
        case (blobIdAsString, createValue) => create(mailboxSession, request.accountId, blobIdAsString, createValue)
      }, ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(UnauthenticatedBlobAccessCreationResults)

  private def create(mailboxSession: MailboxSession,
                     accountId: AccountId,
                     blobIdAsString: String,
                     createValue: JsValue): SMono[UnauthenticatedBlobAccessCreationResult] =
    validateCreateEntry(blobIdAsString, createValue) match {
      case Left(error) => SMono.just(UnauthenticatedBlobAccessCreationFailure(blobIdAsString, error))
      case Right(blobIds) => blobResolvers.validateAccess(blobIds.jmapBlobId, mailboxSession)
        .flatMap {
          case hasAccess if hasAccess.booleanValue() => SMono(tokenRepository.generate(toJavaAccountId(accountId), blobIds.blobStoreBlobId))
          case _ => SMono.error(BlobNotFoundException(blobIds.jmapBlobId))
        }
        .map(token => UnauthenticatedBlobAccessCreationSuccess(blobIdAsString, UnauthenticatedBlobAccessCreationResponse(token.value().toString)): UnauthenticatedBlobAccessCreationResult)
        .onErrorResume(error => SMono.just(UnauthenticatedBlobAccessCreationFailure(blobIdAsString, asSetError(error))))
    }

  private def validateCreateEntry(blobIdAsString: String, createValue: JsValue): Either[SetError, BlobIds] =
    validateCreateValue(createValue)
      .flatMap(_ => parseBlobIds(blobIdAsString))

  private def validateCreateValue(createValue: JsValue): Either[SetError, Unit] =
    createValue match {
      case jsObject: JsObject if jsObject.value.isEmpty => Right(())
      case _: JsObject => Left(SetError.invalidArguments(SetErrorDescription("create value must be an empty object")))
      case _ => Left(SetError.invalidArguments(SetErrorDescription("create value must be an empty object")))
    }

  private def parseBlobIds(blobIdAsString: String): Either[SetError, BlobIds] =
    (for {
      jmapBlobId <- JmapBlobId.of(blobIdAsString)
      blobStoreBlobId <- Try(blobIdFactory.parse(blobIdAsString))
    } yield BlobIds(jmapBlobId, blobStoreBlobId)) match {
      case Success(blobIds) => Right(blobIds)
      case Failure(e) => Left(SetError.invalidArguments(SetErrorDescription(e.getMessage)))
    }

  private def toJavaAccountId(accountId: AccountId): JavaAccountId =
    JavaAccountId.fromString(accountId.id.value)

  private def asSetError(error: Throwable): SetError =
    error match {
      case e: BlobNotFoundException =>
        LOGGER.info("Could not generate unauthenticated blob access token as blob {} is not found", e.blobId)
        SetError.notFound(SetErrorDescription(s"Blob ${e.blobId} could not be found"))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e =>
        LOGGER.error("Failed to generate unauthenticated blob access token", e)
        SetError.serverFail(SetErrorDescription(e.getMessage))
    }

  private case class BlobIds(jmapBlobId: JmapBlobId, blobStoreBlobId: BlobStoreBlobId)
}

sealed trait UnauthenticatedBlobAccessCreationResult

case class UnauthenticatedBlobAccessCreationSuccess(blobId: String,
                                                    response: UnauthenticatedBlobAccessCreationResponse) extends UnauthenticatedBlobAccessCreationResult

case class UnauthenticatedBlobAccessCreationFailure(blobId: String,
                                                    error: SetError) extends UnauthenticatedBlobAccessCreationResult

case class UnauthenticatedBlobAccessCreationResults(results: Seq[UnauthenticatedBlobAccessCreationResult]) {
  def created: Map[String, UnauthenticatedBlobAccessCreationResponse] =
    results.flatMap {
      case success: UnauthenticatedBlobAccessCreationSuccess => Some(success.blobId -> success.response)
      case _ => None
    }.toMap

  def notCreated: Map[String, SetError] =
    results.flatMap {
      case failure: UnauthenticatedBlobAccessCreationFailure => Some(failure.blobId -> failure.error)
      case _ => None
    }.toMap
}
