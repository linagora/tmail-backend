package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.{PublicAssetSerializer => Serializer}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_PUBLIC_ASSETS
import com.linagora.tmail.james.jmap.model.{PublicAssetDTO, PublicAssetGetRequest, PublicAssetGetResponse}
import com.linagora.tmail.james.jmap.publicAsset.{PublicAssetId, PublicAssetIdFactory, PublicAssetRepository, PublicAssetStorage}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{AccountId, Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

class PublicAssetGetMethod @Inject()(val publicAssetRepository: PublicAssetRepository,
                                     val metricFactory: MetricFactory,
                                     val sessionTranslator: SessionTranslator,
                                     val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[PublicAssetGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("PublicAsset/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_PUBLIC_ASSETS)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, PublicAssetGetRequest] =
    Serializer.deserializeGetRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: PublicAssetGetRequest): Publisher[InvocationWithContext] =
    getPublicAssetResponse(mailboxSession.getUser, request)
      .map(response => Invocation(
        methodName = methodName,
        arguments = Arguments(Serializer.serializeGetResponse(response).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

  private def getPublicAssetResponse(username: Username, request: PublicAssetGetRequest): SMono[PublicAssetGetResponse] = {
    request.ids match {
      case None => getAllAssets(username, request.accountId)
      case Some(ids) => getSpecificAssets(username, request.accountId, ids)
    }
  }

  private def getAllAssets(username: Username, accountId: AccountId): SMono[PublicAssetGetResponse] =
    SFlux.fromPublisher(publicAssetRepository.list(username))
      .collectSeq()
      .map(assets => PublicAssetGetResponse(accountId,
        state = UuidState.INSTANCE,
        assets.map(asset => toPublicAssetDTO(asset)),
        Seq.empty))

  private def getSpecificAssets(username: Username, accountId: AccountId, ids: Set[Id]): SMono[PublicAssetGetResponse] = {
    val parsedIds: Set[Either[(String, IllegalArgumentException), PublicAssetId]] = ids.map(id => PublicAssetIdFactory.from(id))

    val assetIds: Set[PublicAssetId] = parsedIds.flatMap({
      case Left(_) => None
      case Right(assetId) => Some(assetId)
    })

    val notFoundIds: Set[String] = parsedIds.flatMap({
      case Left((id, _)) => Some(id)
      case Right(_) => None
    })

    SFlux.fromPublisher(publicAssetRepository.get(username, assetIds))
      .collectSeq()
      .map(assets => {
        PublicAssetGetResponse(accountId,
          state = UuidState.INSTANCE,
          assets.map(asset => toPublicAssetDTO(asset)),
          notFoundIds.toSeq.appendedAll(assetIds.toSeq.diff(assets.map(asset => asset.id)).map(assetId => assetId.asString())))
      })
  }

  private def toPublicAssetDTO(publicAssetStorage: PublicAssetStorage): PublicAssetDTO =
    PublicAssetDTO(id = publicAssetStorage.id,
      publicURI = publicAssetStorage.publicURI,
      size = publicAssetStorage.size,
      contentType = publicAssetStorage.contentType,
      identityIds = publicAssetStorage.identityIds)
}