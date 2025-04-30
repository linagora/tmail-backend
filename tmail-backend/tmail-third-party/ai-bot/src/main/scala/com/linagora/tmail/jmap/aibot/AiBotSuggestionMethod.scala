/** ******************************************************************
 * As a subpart of Twake Mail, this file is edited by Linagora.    *
 * *
 * https://twake-mail.com/                                         *
 * https://linagora.com                                            *
 * *
 * This file is subject to The Affero Gnu Public License           *
 * version 3.                                                      *
 * *
 * https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 * *
 * This program is distributed in the hope that it will be         *
 * useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 * PURPOSE. See the GNU Affero General Public License for          *
 * more details.                                                   *
 * ****************************************************************** */
package com.linagora.tmail.jmap.aibot

import com.linagora.tmail.jmap.aibot.CapabilityIdentifier.LINAGORA_AIBOT
import com.linagora.tmail.jmap.aibot.json.AiBotSerializer
import com.linagora.tmail.mailet.AIRedactionalHelper
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, SessionTranslator}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.model.{FetchGroup, MessageId, MessageResult}
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig
import org.apache.james.util.mime.MessageContentExtractor
import org.reactivestreams.Publisher
import play.api.libs.json._
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

import java.util.Optional

class AiBotSuggestionMethod @Inject()(val aiBotService: AIRedactionalHelper,
                                      val metricFactory: MetricFactory,
                                      val sessionSupplier: SessionSupplier,
                                      val sessionTranslator: SessionTranslator,
                                      val messageIdManager: MessageIdManager,
                                      val messageIdFactory: MessageId.Factory)
  extends MethodRequiringAccountId[AiBotSuggestReplyRequest] {

  override val methodName: Invocation.MethodName = MethodName("AiBot/Suggest")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE,LINAGORA_AIBOT)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, AiBotSuggestReplyRequest] =
    AiBotSerializer.deserializeRequest(invocation.arguments.value).asEither.left.map { error =>
      if (error.toString == "List((/accountId,List(JsonValidationError(List(error.path.missing),List()))))") {
        new IllegalArgumentException("missing accountId")
      }else if(error.toString == "List((/userInput,List(JsonValidationError(List(error.path.missing),List()))))") {
        new IllegalArgumentException("missing UserInput")}
      else {
        ResponseSerializer.asException(error)
      }
    }

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: AiBotSuggestReplyRequest): Publisher[InvocationWithContext] =
    getEmail(mailboxSession, request)
      .flatMap(mailContent => aiBotService.suggestContent(request.userInput, mailContent))
      .map(res =>AiBotSuggestReplyResponse.from(request.accountId,res))
      .map(response =>Invocation(
          methodName,
          Arguments(AiBotSerializer.serializeResponse(response).as[JsObject]),
          invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

private def getEmail(mailboxSession: MailboxSession, request: AiBotSuggestReplyRequest): Mono[Optional[String]] =
  request.emailId match {
    case Some(id) =>
      val messageId: MessageId = messageIdFactory.fromString(id)
      val messageIds = java.util.Collections.singletonList(messageId)

      Mono.from(messageIdManager.getMessagesReactive(
          messageIds,
          FetchGroup.FULL_CONTENT,
          mailboxSession))
        .switchIfEmpty(Mono.error(new IllegalArgumentException(s"MessageId not found: $id")))
        .flatMap(messageResult => extractEmailContent(Mono.just(messageResult)))
    case None =>
      Mono.just(Optional.empty())
  }

  private def extractEmailContent(messagesPublisher: Publisher[MessageResult]): Mono[Optional[String]] =
    Mono.from(messagesPublisher)
      .flatMap(messageResult => {
        val contentStream = messageResult.getFullContent.getInputStream
        Mono.fromCallable(() => {
          val messageBuilder = new DefaultMessageBuilder()
          messageBuilder.setMimeEntityConfig(MimeConfig.DEFAULT)
          val mimeMessage: Message = messageBuilder.parseMessage(contentStream)
          val ext = new MessageContentExtractor
          val extractor = ext.extract(mimeMessage)
          extractor.getTextBody
        }).subscribeOn(Schedulers.boundedElastic())
      })
}