package com.linagora.tmail.james.jmap.model

import java.net.URI
import java.util.function.Predicate

import com.google.common.base.Preconditions
import net.fortuna.ical4j.model.component.{VEvent, VTimeZone}
import net.fortuna.ical4j.model.parameter.{Cn, CuType, PartStat, Role}
import net.fortuna.ical4j.model.property.{Attendee, DtStamp, LastModified, Method}
import net.fortuna.ical4j.model.{Calendar, DateTime, Property, PropertyList}
import net.fortuna.ical4j.validate.ValidationResult
import org.apache.james.core.MailAddress

import scala.jdk.CollectionConverters._

case class AttendeeReply(attendee: MailAddress, partStat: PartStat, cn: Option[Cn] = None)

object CalendarEventReplyGenerator {

  val PRODID: String = "-//Linagora//TMail Calendar//EN"

  def generate(eventRequest: Calendar, attendeeReply: AttendeeReply): Calendar = {
    Preconditions.checkArgument(eventRequest.getMethod.equals(Method.REQUEST), "The method of the event request must be REQUEST".asInstanceOf[Object])
    val requestVEventOpt: Option[VEvent] = eventRequest.getComponents[VEvent]("VEVENT").asScala.headOption
    Preconditions.checkArgument(requestVEventOpt.isDefined, "The event request must be a VEVENT".asInstanceOf[Object])

    val requestVEvent: VEvent = requestVEventOpt.get
    val timeZone: Option[VTimeZone] = requestVEvent.getComponents[VTimeZone]("VTIMEZONE").asScala.headOption

    def notProperties(properties: String*): Predicate[Property] = property => !properties.contains(property.getName)

    val replyProperty: PropertyList[Property] = Some(new PropertyList[Property])
      .map(propertyList => {
        requestVEvent.getProperties
          .stream()
          .filter(notProperties("ATTENDEE", "DTSTAMP", "LAST-MODIFIED"))
          .forEach(e => propertyList.add(e))

        propertyList.add(buildReplyAttendee(attendeeReply))

        val replyTime: DateTime = new DateTime(true)
        propertyList.add(new DtStamp(replyTime))
        propertyList.add(new LastModified(replyTime))

        propertyList
      }).get

    val validationResult: Seq[ValidationResult] = replyProperty.asScala
      .map(_.validate())
      .filter(_.hasErrors)
      .toSeq

    Preconditions.checkArgument(validationResult.isEmpty, s"Invalidate calendar event: ${validationResultAsString(validationResult)}".asInstanceOf[Object])

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

  private def validationResultAsString(input: Seq[ValidationResult]): String =
    input.flatMap(_.getEntries.asScala.toSeq).map(error => s"${error.getContext} : ${error.getMessage}").mkString(";")
}
