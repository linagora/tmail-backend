package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.encrypted.{EncryptedEmailContentStore, EncryptedEmailFastView, MessageNotFoundException}
import com.linagora.tmail.james.jmap.json.EncryptedEmailSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_PGP
import com.linagora.tmail.james.jmap.model.{EmailIdHelper, EncryptedEmailFastViewResults, EncryptedEmailGetRequest, EncryptedEmailGetResponse}
import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.mail.UnparsedEmailId
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.{BlobResolver, SessionSupplier}
import org.apache.james.mailbox.model.MessageId
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class EncryptedEmailFastViewGetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[EncryptedEmailFastViewGetMethod])
    Multibinder.newSetBinder(binder(), classOf[BlobResolver])
      .addBinding()
      .to(classOf[EncryptedAttachmentBlobResolver])
  }
}

class EncryptedEmailFastViewGetMethod @Inject()(encryptedEmailFastViewReader: EncryptedEmailFastViewReader,
                                                messageIdFactory: MessageId.Factory,
                                                val metricFactory: MetricFactory,
                                                val sessionTranslator: SessionTranslator,
                                                val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[EncryptedEmailGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("EncryptedEmailFastView/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_PGP)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: EncryptedEmailGetRequest): Publisher[InvocationWithContext] =
    computeResponse(request, mailboxSession)
      .map(response => Invocation(
        methodName = methodName,
        arguments = Arguments(EncryptedEmailSerializer.serializeEncryptedEmailGetResponse(response).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, EncryptedEmailGetRequest] =
    EncryptedEmailSerializer.deserializeEncryptedEmailGetRequest(invocation.arguments.value)
      .asEitherRequest
      .flatMap(request => request.validate)

  private def computeResponse(request: EncryptedEmailGetRequest,
                              mailboxSession: MailboxSession): SMono[EncryptedEmailGetResponse] =
    computeResults(request, mailboxSession)
      .map(result => EncryptedEmailGetResponse(
        accountId = request.accountId,
        state = UuidState.INSTANCE,
        list = result.list,
        notFound = result.notFound))

  private def computeResults(request: EncryptedEmailGetRequest,
                             mailboxSession: MailboxSession): SMono[EncryptedEmailFastViewResults] = {
    val parsedIds: List[Either[(UnparsedEmailId, IllegalArgumentException), MessageId]] =
      EmailIdHelper.parser(request.ids.value, messageIdFactory)

    val messagesIds: List[MessageId] = parsedIds.flatMap({
      case Left(_) => None
      case Right(messageId) => Some(messageId)
    })

    val parsingErrors: SFlux[EncryptedEmailFastViewResults] = SFlux.fromIterable(parsedIds.flatMap({
      case Left((id, _)) =>
        Some(EncryptedEmailFastViewResults.notFound(id))
      case Right(_) => None
    }))

    SFlux.merge(Seq(parsingErrors, retrieveEmails(messagesIds, mailboxSession)))
      .reduce(EncryptedEmailFastViewResults.empty())(EncryptedEmailFastViewResults.merge)
  }

  private def retrieveEmails(ids: List[MessageId], mailboxSession: MailboxSession): SFlux[EncryptedEmailFastViewResults] = {
    encryptedEmailFastViewReader.read(ids, mailboxSession)
      .map(messageIdAndEitherFastView => messageIdAndEitherFastView._2 match {
        case Left(_) => EncryptedEmailFastViewResults.notFound(messageIdAndEitherFastView._1)
        case Right(fastView) => EncryptedEmailFastViewResults.list(fastView)
      })
  }
}

private class EncryptedEmailFastViewReader @Inject()(encryptedEmailContentStore: EncryptedEmailContentStore,
                                                     messageIdManager: MessageIdManager) {

  def read(messageIds: List[MessageId], mailboxSession: MailboxSession): SFlux[(MessageId, Either[MessageNotFoundException, EncryptedEmailFastView])] =
    SMono.fromPublisher(messageIdManager.accessibleMessagesReactive(messageIds.asJava, mailboxSession))
      .map(messageIdSet => messageIdSet.asScala.toList)
      .flatMapMany(messageIdList => SFlux.merge(Seq(
        retrieveEncryptedEmailFastView(messageIdList),
        SFlux.fromIterable(messageIds diff messageIdList)
          .map(messageId => messageId -> Left(MessageNotFoundException(messageId))))))

  private def retrieveEncryptedEmailFastView(messageIds: List[MessageId]): SFlux[(MessageId, Either[MessageNotFoundException, EncryptedEmailFastView])] =
    SFlux.fromIterable(messageIds)
      .flatMap(messageId => retrieveEncryptedEmailFastView(messageId), DEFAULT_CONCURRENCY)

  private def retrieveEncryptedEmailFastView(messageId: MessageId): SMono[(MessageId, Either[MessageNotFoundException, EncryptedEmailFastView])] =
    SMono.fromPublisher(encryptedEmailContentStore.retrieveFastView(messageId))
      .map(fastView => messageId -> scala.Right(fastView))
      .onErrorResume {
        case messageNotFound: MessageNotFoundException => SMono.just(messageId -> Left(messageNotFound))
        case error => SMono.error(error)
      }
}