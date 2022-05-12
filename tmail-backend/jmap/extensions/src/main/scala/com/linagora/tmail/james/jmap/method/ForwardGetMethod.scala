package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.ForwardSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FORWARD
import com.linagora.tmail.james.jmap.model.{ForwardGetRequest, ForwardGetResponse, ForwardNotFound, Forwards, UnparsedForwardId}
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.core.{AccountId, Invocation}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.rrt.api.RecipientRewriteTable
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsSuccess}

import javax.inject.Inject

object ForwardGetResult {
  def empty: ForwardGetResult = ForwardGetResult(Set.empty, ForwardNotFound(Set.empty))
  def merge(result1: ForwardGetResult, result2: ForwardGetResult): ForwardGetResult = result1.merge(result2)
  def found(forwards: Forwards): ForwardGetResult =
    ForwardGetResult(Set(forwards), ForwardNotFound(Set.empty))
  def notFound(forwardId: UnparsedForwardId): ForwardGetResult =
    ForwardGetResult(Set.empty, ForwardNotFound(Set(forwardId)))
}

case class ForwardGetResult(forwards: Set[Forwards], notFound: ForwardNotFound) {
  def merge(other: ForwardGetResult): ForwardGetResult =
    ForwardGetResult(this.forwards ++ other.forwards, this.notFound.merge(other.notFound))
  def asResponse(accountId: AccountId): ForwardGetResponse =
    ForwardGetResponse(
      accountId = accountId,
      state = INSTANCE,
      list = forwards.toList,
      notFound = notFound)
}

class ForwardGetMethod @Inject()(recipientRewriteTable: RecipientRewriteTable,
                                 serializer: ForwardSerializer,
                                 val metricFactory: MetricFactory,
                                 val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[ForwardGetRequest] {
  override val methodName: Invocation.MethodName = MethodName("Forward/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_FORWARD)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: ForwardGetRequest): Publisher[InvocationWithContext] = ???

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, ForwardGetRequest] =
    serializer.deserializeForwardGetRequest(invocation.arguments.value) match {
      case JsSuccess(forwardGetRequest, _) => Right(forwardGetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }
}
