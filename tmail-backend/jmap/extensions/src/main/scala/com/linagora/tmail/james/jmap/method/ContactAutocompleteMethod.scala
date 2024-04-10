package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine
import com.linagora.tmail.james.jmap.json.ContactSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CONTACT
import com.linagora.tmail.james.jmap.model.{Contact, ContactAutocompleteRequest, ContactAutocompleteResponse, ContactFirstname, ContactId, ContactSurname}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, Invocation, Limit, SessionTranslator, UrlPrefixes}
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

case object ContactCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object ContactCapability extends Capability {
  val properties: CapabilityProperties = ContactCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_CONTACT
}

class ContactCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = ContactCapabilityFactory
}

case object ContactCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = ContactCapability

  override def id(): CapabilityIdentifier = LINAGORA_CONTACT
}

class ContactAutocompleteMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new ContactCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[ContactAutocompleteMethod])
  }
}

class ContactAutocompleteMethod @Inject()(serializer: ContactSerializer,
                                          emailAddressContactSearchEngine: EmailAddressContactSearchEngine,
                                          val sessionTranslator: SessionTranslator,
                                          val metricFactory: MetricFactory,
                                          val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[ContactAutocompleteRequest] {
  override val methodName: Invocation.MethodName = MethodName("TMailContact/autocomplete")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(LINAGORA_CONTACT)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext,
                         mailboxSession: MailboxSession, request: ContactAutocompleteRequest): Publisher[InvocationWithContext] =
    processRequest(mailboxSession, invocation.invocation, request)
      .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, ContactAutocompleteRequest] =
    serializer.deserialize(invocation.arguments.value).asEitherRequest

  private def processRequest(mailboxSession: MailboxSession,
                             invocation: Invocation,
                             request: ContactAutocompleteRequest): SMono[Invocation] =
    Limit.validateRequestLimit(request.limit)
      .fold(SMono.error,
        limit => executeAutocomplete(mailboxSession, request, limit)
          .map(response => Invocation(
            methodName = methodName,
            arguments = Arguments(serializer.serialize(response)),
            methodCallId = invocation.methodCallId)))

  private def executeAutocomplete(session: MailboxSession, request: ContactAutocompleteRequest, limit: Limit.Limit): SMono[ContactAutocompleteResponse] =
    SFlux.fromPublisher(emailAddressContactSearchEngine.autoComplete(
        AccountId.fromUsername(session.getUser), request.filter.text.value, limit.value))
      .map(contact => Contact(ContactId(contact.id),
        contact.fields.address,
        ContactFirstname(contact.fields.firstname),
        ContactSurname(contact.fields.surname)))
      .collectSeq()
      .map(contacts => ContactAutocompleteResponse(
        accountId = request.accountId,
        list = contacts,
        limit = Some(limit).filterNot(used => request.limit.map(_.value).contains(used.value))))
}
