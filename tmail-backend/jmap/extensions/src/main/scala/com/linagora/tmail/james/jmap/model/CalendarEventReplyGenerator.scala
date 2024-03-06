package com.linagora.tmail.james.jmap.model

import com.google.common.base.Preconditions
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.{CuType, PartStat, Role}
import net.fortuna.ical4j.model.property.{Attendee, Method}
import net.fortuna.ical4j.model.{Calendar, Property, PropertyList}
import org.apache.james.core.Username

import java.net.URI
import scala.jdk.CollectionConverters._

object CalendarEventReplyGenerator {
  def generate(eventRequest: Calendar, attendee: Username, partStat: PartStat): Calendar = {
    Preconditions.checkArgument(eventRequest.getMethod.equals(Method.REQUEST), "The method of the event request must be REQUEST".asInstanceOf[Object])
    val requestVEvent: VEvent = eventRequest.getComponents("VEVENT").asInstanceOf[java.util.List[VEvent]].asScala.head

    val replyProperty: PropertyList[Property] = Some(new PropertyList[Property])
      .map(propertyList => {
        requestVEvent.getProperties
          .stream()
          .filter(!_.getName.equals("ATTENDEE"))
          .forEach(e => propertyList.add(e))
        propertyList.add(buildReplyAttendee(attendee, partStat))
        propertyList
      }).get

    val calendar = new Calendar()
      .withDefaults()
      .withComponent(new VEvent(replyProperty).getFluentTarget)
      .withProperty(Method.REPLY)

    Option(eventRequest.getCalendarScale).map(calScale => calendar.withProperty(calScale))
    calendar.getFluentTarget
  }

  private def buildReplyAttendee(attendee: Username, partStat: PartStat): Attendee =
    new Attendee(URI.create("mailto:" + attendee.asString()))
      .withParameter(Role.REQ_PARTICIPANT)
      .withParameter(partStat)
      .withParameter(CuType.INDIVIDUAL)
      .getFluentTarget
}
