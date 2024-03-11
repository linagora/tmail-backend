package com.linagora.tmail.james.jmap.model

import java.net.URI

import com.google.common.base.Preconditions
import net.fortuna.ical4j.model.component.{VEvent, VTimeZone}
import net.fortuna.ical4j.model.parameter.{Cn, CuType, PartStat, Role}
import net.fortuna.ical4j.model.property.{Attendee, Method}
import net.fortuna.ical4j.model.{Calendar, Property, PropertyList}
import org.apache.james.core.Username

import scala.jdk.CollectionConverters._

case class AttendeeReply(attendee: Username, partStat: PartStat, cn : Option[Cn] = None)

object CalendarEventReplyGenerator {

  val PRODID: String = "-//Linagora//TMail Calendar//EN"

  def generate(eventRequest: Calendar, attendeeReply: AttendeeReply): Calendar = {
    Preconditions.checkArgument(eventRequest.getMethod.equals(Method.REQUEST), "The method of the event request must be REQUEST".asInstanceOf[Object])
    val requestVEventOpt: Option[VEvent] = eventRequest.getComponents[VEvent]("VEVENT").asScala.headOption
    Preconditions.checkArgument(requestVEventOpt.isDefined, "The event request must be a VEVENT".asInstanceOf[Object])

    val requestVEvent: VEvent = requestVEventOpt.get
    val timeZone: Option[VTimeZone] = requestVEvent.getComponents[VTimeZone]("VTIMEZONE").asScala.headOption

    val attendeeInRequest: Option[Attendee] = requestVEvent.getProperties[Attendee]("ATTENDEE").asScala.toSeq
      .find(attendee => attendeeReply.attendee.asString().equals(attendee.getCalAddress.getSchemeSpecificPart))

    Preconditions.checkArgument(attendeeInRequest.isDefined, "Can not reply when not invited to attend".asInstanceOf[Object])

    val replyProperty: PropertyList[Property] = Some(new PropertyList[Property])
      .map(propertyList => {
        requestVEvent.getProperties
          .stream()
          .filter(!_.getName.equals("ATTENDEE"))
          .forEach(e => propertyList.add(e))
        propertyList.add(buildReplyAttendee(attendeeReply))
        propertyList
      }).get

    val calendar = new Calendar()
      .withDefaults()
      .withComponent(new VEvent(replyProperty).getFluentTarget)
      .withProdId(PRODID)
      .withProperty(Method.REPLY)

    Option(eventRequest.getCalendarScale).map(calScale => calendar.withProperty(calScale))
    timeZone.map(calendar.withComponent)
    calendar.getFluentTarget
  }

  private def buildReplyAttendee(attendeeReply: AttendeeReply): Attendee =
    Some(new Attendee(URI.create("mailto:" + attendeeReply.attendee.asString()))
      .withParameter(Role.REQ_PARTICIPANT)
      .withParameter(attendeeReply.partStat)
      .withParameter(CuType.INDIVIDUAL))
      .map(attendee => {
        attendeeReply.cn.map(cn => attendee.withParameter(cn))
        attendee
      }).get.getFluentTarget
}
