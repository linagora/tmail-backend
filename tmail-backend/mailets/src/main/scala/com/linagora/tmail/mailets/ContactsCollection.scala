package com.linagora.tmail.mailets

import com.google.common.collect.ImmutableSet
import com.linagora.tmail.james.jmap.contact.{ContactFields, TmailContactUserAddedEvent}
import javax.inject.Inject
import javax.mail.Message
import javax.mail.internet.InternetAddress
import org.apache.commons.collections.CollectionUtils
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.events.Event.EventId
import org.apache.james.events.{EventBus, RegistrationKey}
import org.apache.james.mime4j.util.MimeUtil
import org.apache.mailet.base.GenericMailet
import org.apache.mailet.{Attribute, AttributeName, AttributeValue, Mail, MailetException}
import play.api.libs.json.{JsValue, Json, Writes}
import reactor.core.scala.publisher.{SFlux, SMono}

class ContactsCollection @Inject()(eventBus: EventBus) extends GenericMailet {

  private var attributeName: AttributeName = _
  private val NO_REGISTRATION_KEYS: ImmutableSet[RegistrationKey] = ImmutableSet.of

  override def init(): Unit =
    attributeName = Option(getInitParameter("attribute"))
      .map(AttributeName.of)
      .getOrElse(throw new MailetException("No value for `attribute` parameter was provided."))

  override def service(mail: Mail): Unit =
    if (CollectionUtils.isNotEmpty(mail.getRecipients)) {
      val contacts: Seq[ContactFields] = extractRecipientsContacts(mail)
      dispatchEvents(contacts)
      appendAttributeToMail(mail, contacts)
    }

  private def extractRecipientsContacts(mail: Mail): Seq[ContactFields] =
    Seq(Message.RecipientType.TO, Message.RecipientType.CC, Message.RecipientType.BCC)
      .flatMap(recipientType => Option(mail.getMessage.getHeader(recipientType.toString, ",")))
      .flatMap(header => InternetAddress.parseHeader(header, false).toSeq)
      .map(_.toString)
      .map(MimeUtil.unscrambleHeaderValue)
      .map(new InternetAddress(_))
      .map(extractContactField)

  private def extractContactField(internetAddress: InternetAddress): ContactFields  =
    ContactFields(new MailAddress(internetAddress), firstname = Option(internetAddress.getPersonal).getOrElse(""))

  private def dispatchEvents(contacts: Seq[ContactFields]): Unit =
    SFlux.fromIterable(contacts)
      .flatMap(contact => SMono.fromPublisher(eventBus.dispatch(TmailContactUserAddedEvent(
        eventId = EventId.random(),
        username = Username.fromMailAddress(contact.address),
        contact = contact),
        NO_REGISTRATION_KEYS)))
      .collectSeq()
      .block()

  private def appendAttributeToMail(mail: Mail, contacts: Seq[ContactFields]): Unit =
    mail.setAttribute(new Attribute(attributeName,
      AttributeValue.of(ContactFieldSerializer.serialize(contacts).toString())))
}

object ContactFieldSerializer {

  private implicit val contactFieldReads: Writes[ContactFields] = (contact: ContactFields) => Json.obj(
    "address" -> contact.address.asString(),
    "firstname" -> contact.firstname,
    "surname" -> contact.surname
  )

  def serialize(contactFields: Seq[ContactFields]): JsValue = Json.toJson(contactFields)
}
