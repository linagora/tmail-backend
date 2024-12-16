package com.linagora.tmail.james.jmap.contact

import java.util

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.collect.ImmutableList
import jakarta.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.events.Event.EventId
import org.apache.james.events.{Event, EventSerializer}
import org.apache.james.jmap.change.{EventDTO, EventDTOModule}
import org.apache.james.json.JsonGenericSerializer

case class TmailJmapEventSerializer @Inject()() extends EventSerializer {
  private val genericSerializer: JsonGenericSerializer[TmailContactUserAddedEvent, TmailContactUserAddedEventDTO] =
    JsonGenericSerializer.forModules(TmailContactUserAddedEventDTOFactory.dtoModule)
      .withoutNestedType()

  override def toJson(event: Event): String = event match {
    case tmailContactUserAddedEvent: TmailContactUserAddedEvent => genericSerializer.serialize(tmailContactUserAddedEvent)
  }

  override def asEvent(serialized: String): Event = genericSerializer.deserialize(serialized)

  override def toJsonBytes(event: Event): Array[Byte] = event match {
    case tmailContactUserAddedEvent: TmailContactUserAddedEvent => genericSerializer.serializeToBytes(tmailContactUserAddedEvent)
  }

  override def fromBytes(serialized: Array[Byte]): Event = genericSerializer.deserializeFromBytes(serialized)

  override def toJson(event: util.Collection[Event]): String = {
    if (event.size() != 1) {
      throw new IllegalArgumentException("Not supported for multiple events, please serialize separately")
    }
    toJson(event.iterator().next())
  }

  override def asEvents(serialized: String): util.List[Event] = ImmutableList.of(asEvent(serialized))
}

case class TmailContactUserAddedEventDTO(@JsonProperty("type") getType: String,
                                         @JsonProperty("eventId") getEventId: String,
                                         @JsonProperty("username") getUsername: String,
                                         @JsonProperty("contactAddress") getContactAddress: String,
                                         @JsonProperty("contactFirstname") getContactFirstname: String,
                                         @JsonProperty("contactSurname") getContactSurname: String) extends EventDTO {

  def toDomainObject(): TmailContactUserAddedEvent =
    TmailContactUserAddedEvent(
      eventId = EventId.of(getEventId),
      username = Username.of(getUsername),
      contact = ContactFields(address = new MailAddress(getContactAddress), firstname = getContactFirstname, surname = getContactSurname))

}

object TmailContactUserAddedEventDTOFactory {
  val dtoModule: EventDTOModule[TmailContactUserAddedEvent, TmailContactUserAddedEventDTO] =
    EventDTOModule.forEvent(classOf[TmailContactUserAddedEvent])
      .convertToDTO(classOf[TmailContactUserAddedEventDTO])
      .toDomainObjectConverter(_.toDomainObject())
      .toDTOConverter((event, _) => toDTO(event))
      .typeName(classOf[TmailContactUserAddedEvent].getCanonicalName)
      .withFactory(EventDTOModule.apply)

  def toDTO(event: TmailContactUserAddedEvent): TmailContactUserAddedEventDTO =
    TmailContactUserAddedEventDTO(
      getType = classOf[TmailContactUserAddedEvent].getCanonicalName,
      getEventId = event.getEventId.getId.toString,
      getUsername = event.username.asString(),
      getContactAddress = event.contact.address.asString(),
      getContactFirstname = event.contact.firstname,
      getContactSurname = event.contact.surname)
}