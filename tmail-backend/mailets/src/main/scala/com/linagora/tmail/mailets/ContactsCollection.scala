package com.linagora.tmail.mailets

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.google.common.collect.{ImmutableList, ImmutableSet}
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys
import com.linagora.tmail.james.jmap.contact.{ContactFields, TmailContactUserAddedEvent}
import javax.inject.{Inject, Named}
import javax.mail.Message
import javax.mail.internet.InternetAddress
import org.apache.commons.collections.CollectionUtils
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.events.Event.EventId
import org.apache.james.events.{EventBus, RegistrationKey}
import org.apache.james.mime4j.util.MimeUtil
import org.apache.james.transport.mailets.ContactExtractor.ExtractedContacts
import org.apache.mailet.base.GenericMailet
import org.apache.mailet.{Attribute, AttributeName, AttributeValue, Mail, MailetException}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

/**
 * <p><b>ContactsCollection</b> allows extracting the recipient's contact of a message
 *  and dispatch ContactUserAddedEvent to eventBus, in order to index contact asynchronously.
 *  This mailet also stores them as JSON in a specified message attribute.</p>
 *
 * <p>Here is the JSON format:</p>
 * <pre><code>
 * {
 *   "userEmail" : "sender@james.org",
 *   "emails" : [ "to@james.org", "cc@james.org" ]
 * }
 * </code></pre>
 *
 * <p>Sample configuration:</p>
 *
 * <pre><code>
 * &lt;mailet match="All" class="ContactsCollection"&gt;
 *   &lt;attribute&gt;ExtractedContacts&lt;/attribute&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */

class ContactsCollection @Inject()(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) eventBus: EventBus) extends GenericMailet {

  private val NO_REGISTRATION_KEYS: ImmutableSet[RegistrationKey] = ImmutableSet.of
  private val OBJECT_MAPPER: ObjectMapper = new ObjectMapper().registerModule(new Jdk8Module)
  private var attributeName: AttributeName = _

  override def init(): Unit =
    attributeName = Option(getInitParameter("attribute"))
      .map(AttributeName.of)
      .getOrElse(throw new MailetException("No value for `attribute` parameter was provided."))

  override def service(mail: Mail): Unit =
    if (CollectionUtils.isNotEmpty(mail.getRecipients)) {
     SMono.justOrEmpty(mail.getMaybeSender.asOptional().toScala)
        .map(sender => extractRecipientsContacts(mail) -> sender)
        .flatMap {
          case (contacts, sender) => SFlux.zip(appendAttributeToMail(sender, mail, contacts), dispatchEvents(sender, contacts))
            .`then`()
        }.block()
    }

  private def extractRecipientsContacts(mail: Mail): Seq[ContactFields] =
    Seq(Message.RecipientType.TO, Message.RecipientType.CC, Message.RecipientType.BCC)
      .flatMap(recipientType => Option(mail.getMessage.getHeader(recipientType.toString, ",")))
      .flatMap(header => InternetAddress.parseHeader(header, false).toSeq)
      .map(extractContactField)

  private def extractContactField(internetAddress: InternetAddress): ContactFields =
    ContactFields(new MailAddress(internetAddress), firstname = Option(internetAddress.getPersonal).getOrElse(""))

  private def dispatchEvents(sender: MailAddress, contacts: Seq[ContactFields]): SMono[Unit] =
    SFlux.fromIterable(contacts)
      .flatMap(contact => SMono.fromPublisher(eventBus.dispatch(TmailContactUserAddedEvent(
        eventId = EventId.random(),
        username = Username.fromMailAddress(sender),
        contact = contact),
        NO_REGISTRATION_KEYS)))
      .collectSeq()
      .`then`()

  private def appendAttributeToMail(sender: MailAddress, mail: Mail, contacts: Seq[ContactFields]): SMono[String] =
    SMono.just(sender)
      .map(mailAddress => new ExtractedContacts(mailAddress.asString, ImmutableList.copyOf(contacts.map(_.address.asString()).asJava)))
      .map(extractedContact => OBJECT_MAPPER.writeValueAsString(extractedContact))
      .doOnNext(extractedContactJson => mail.setAttribute(new Attribute(attributeName, AttributeValue.of(extractedContactJson))))
}

