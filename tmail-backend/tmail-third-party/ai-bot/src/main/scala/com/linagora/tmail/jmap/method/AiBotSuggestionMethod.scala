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
package com.linagora.tmail.jmap.method

import jakarta.inject.Inject

import java.util.Optional
import eu.timepit.refined.auto._
import com.linagora.tmail.mailet.AIRedactionalHelper
import com.linagora.tmail.jmap.mail.{AiBotSuggestReplyRequest, AiBotSuggestReplyResponse}
import com.linagora.tmail.jmap.core.CapabilityIdentifier.LINAGORA_AIBOT
import com.linagora.tmail.jmap.json.AiBotSerializer
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.{Invocation, SessionTranslator}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.model.{FetchGroup, MessageId, MessageResult}
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json._
import reactor.core.publisher.{Flux, Mono}

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.jdk.CollectionConverters._

class AiBotSuggestionMethod @Inject()(val aiBotService: AIRedactionalHelper,
                                      val metricFactory: MetricFactory,
                                      val sessionSupplier: SessionSupplier,
                                      val sessionTranslator: SessionTranslator,
                                      val messageIdManager: MessageIdManager,
                                      val messageIdFactory: MessageId.Factory)
  extends MethodRequiringAccountId[AiBotSuggestReplyRequest] {

  override val methodName: Invocation.MethodName = MethodName("AiBotSuggestion/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE,LINAGORA_AIBOT)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, AiBotSuggestReplyRequest] =
    AiBotSerializer.deserializeRequest(invocation.arguments.value).asEither.left.map(ResponseSerializer.asException)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: AiBotSuggestReplyRequest): Publisher[InvocationWithContext] = {
    getEmail(mailboxSession, request)
      .flatMap(result => aiBotService.suggestContent(request.userInput, Optional.ofNullable(result))
        .map(res => AiBotSuggestReplyResponse.from(request.accountId,res))
        .map(response => Invocation(
          methodName,
          Arguments(AiBotSerializer.serializeResponse(response).as[JsObject]),
          invocation.invocation.methodCallId))
        .map(InvocationWithContext(_, invocation.processingContext)))
  }

  private def getEmail(mailboxSession: MailboxSession, request: AiBotSuggestReplyRequest) = {
    val messageId: MessageId = messageIdFactory.fromString(request.emailId)
    val messageIds = java.util.Collections.singletonList(messageId)
    val fetchGroup = FetchGroup.FULL_CONTENT
    val messagesPublisher: Publisher[MessageResult] =
      messageIdManager.getMessagesReactive(
        messageIds,
        fetchGroup,
        mailboxSession)

    val emailContentAsString = extractEmailContent(messagesPublisher)
    emailContentAsString
  }

  private def extractEmailContent(messagesPublisher: Publisher[MessageResult]): Mono[String] = {
    val mono = Mono.from(messagesPublisher)

    mono.map { messageResult =>
      val inputStream = messageResult.getBody.getInputStream
      messageResult.getFullContent.getInputStream
      try {
        inputStream.readAllBytes()
      } finally {
        inputStream.close()
      }
    }.map { bytes =>
      new String(bytes, StandardCharsets.UTF_8)
    }
  }
}


