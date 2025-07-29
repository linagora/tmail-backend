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

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.contact.{EmailAddressContactSearchEngine, MinAutoCompleteInputLength}
import com.linagora.tmail.james.jmap.json.ContactSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CONTACT
import com.linagora.tmail.james.jmap.model.{Contact, ContactAutocompleteRequest, ContactAutocompleteResponse, ContactFirstname, ContactId, ContactSurname}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
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

case class ContactCapabilityProperties(minAutoCompleteInputLength: MinAutoCompleteInputLength) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj("minInputLength" -> minAutoCompleteInputLength.value)
}

final case class ContactCapability(properties: ContactCapabilityProperties,
                                   identifier: CapabilityIdentifier = LINAGORA_CONTACT) extends Capability

class ContactCapabilitiesModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[ContactCapabilityFactory])
  }
}

class ContactCapabilityFactory @Inject()(val minAutoCompleteInputLength: MinAutoCompleteInputLength) extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability =
    ContactCapability(ContactCapabilityProperties(minAutoCompleteInputLength))

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
