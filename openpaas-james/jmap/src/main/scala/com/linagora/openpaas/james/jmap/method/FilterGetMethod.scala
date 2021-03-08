package com.linagora.openpaas.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.openpaas.james.jmap.json.FilterSerializer
import com.linagora.openpaas.james.jmap.method.CapabilityIdentifier.LINAGORA_FILTER
import com.linagora.openpaas.james.jmap.model.{Filter, FilterGetNotFound, FilterGetRequest, FilterGetResponse, Rule}
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.api.filtering.FilteringManagement
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Capability, CapabilityProperties, Invocation}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MailboxId
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

import javax.inject.Inject

case object FilterCapabilityProperties extends CapabilityProperties

case object FilterCapability extends Capability {
  val properties: CapabilityProperties = FilterCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_FILTER
}

class FilterCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): Capability = FilterCapability
}

class FilterGetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new FilterCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[FilterGetMethod])
  }
}

class FilterGetMethod @Inject()(val metricFactory: MetricFactory,
                                val sessionSupplier: SessionSupplier,
                                val mailboxIdFactory: MailboxId.Factory,
                                filteringManagement: FilteringManagement) extends MethodRequiringAccountId[FilterGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("Filter/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(LINAGORA_FILTER)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession,
                         request: FilterGetRequest): Publisher[InvocationWithContext] =
    getFilterGetResponse(request, mailboxSession).map(response => InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(FilterSerializer.serialize(response).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = invocation.processingContext))


  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, FilterGetRequest] =
    FilterSerializer.deserializeFilterGetRequest(invocation.arguments.value) match {
      case JsSuccess(filterGetRequest, _) => Right(filterGetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def retrieveFilters(username: Username) : SMono[Filter] =
    SFlux.fromPublisher(filteringManagement.listRulesForUser(username))
      .map(javaRule => Rule.fromJava(javaRule, mailboxIdFactory))
      .collectSeq()
      .map(rules => Filter("singleton", rules.toList))

  private def getFilterGetResponse(request: FilterGetRequest,
                                   mailboxSession: MailboxSession): SMono[FilterGetResponse] =
    request.ids match {
      case None => retrieveFilters(mailboxSession.getUser)
        .map(filter => FilterGetResponse(request.accountId, List(filter), FilterGetNotFound(List())))
      case Some(ids) => if(ids.value.contains("singleton")) {
        retrieveFilters(mailboxSession.getUser)
          .map(filter => FilterGetResponse(request.accountId, List(filter), FilterGetNotFound(ids.value.filterNot(id => id.equals("singleton")))))
      } else {
        SMono.just(FilterGetResponse(request.accountId, List(), FilterGetNotFound(ids.value)))
      }
    }

}

