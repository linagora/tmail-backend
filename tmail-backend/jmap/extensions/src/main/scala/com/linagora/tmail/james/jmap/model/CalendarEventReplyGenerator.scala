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

package com.linagora.tmail.james.jmap.model

import java.net.URI
import java.time.Instant
import java.util
import java.util.function.Predicate

import com.google.common.base.Preconditions
import net.fortuna.ical4j.model.component.{VEvent, VTimeZone}
import net.fortuna.ical4j.model.parameter.{Cn, CuType, PartStat, Role}
import net.fortuna.ical4j.model.property.immutable.ImmutableMethod
import net.fortuna.ical4j.model.property.{Attendee, DtStamp, LastModified, Method}
import net.fortuna.ical4j.model.{Calendar, Component, Property, PropertyList}
import net.fortuna.ical4j.validate.ValidationResult
import org.apache.james.core.MailAddress

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

case class AttendeeReply(attendee: MailAddress, partStat: PartStat, cn: Option[Cn] = None)

object CalendarEventReplyGenerator {

  val PRODID: String = "-//Linagora//TMail Calendar//EN"

  def generate(eventRequest: Calendar, attendeeReply: AttendeeReply): Calendar = {
    Preconditions.checkArgument(Method.VALUE_REQUEST.equalsIgnoreCase(eventRequest.getMethod.getValue), "The method of the event request must be REQUEST".asInstanceOf[Object])
    val requestVEventOpt: Option[VEvent] = eventRequest.getComponents[VEvent](Component.VEVENT).asScala.headOption
    Preconditions.checkArgument(requestVEventOpt.isDefined, "The event request must be a VEVENT".asInstanceOf[Object])

    val requestVEvent: VEvent = requestVEventOpt.get
    val timeZone: Option[VTimeZone] = eventRequest.getComponent[VTimeZone](Component.VTIMEZONE).toScala

    def notProperties(properties: String*): Predicate[Property] = property => !properties.contains(property.getName)

    val replyProperty: util.ArrayList[Property] = Some(new util.ArrayList[Property]())
      .map(propertyList => {
        requestVEvent.getProperties[Property]
          .stream()
          .filter(notProperties(Property.ATTENDEE, Property.DTSTAMP, Property.LAST_MODIFIED))
          .forEach(e => propertyList.add(e))

        propertyList.add(buildReplyAttendee(attendeeReply))
        val replyTime: Instant = Instant.now
        propertyList.add(new DtStamp(replyTime))
        propertyList.add(new LastModified(replyTime))

        propertyList
      }).get

    val replyPropertyList = new PropertyList(replyProperty)
    val validationResult: Seq[ValidationResult] = replyPropertyList.getAll.asScala
      .map(_.validate())
      .filter(_.hasErrors)
      .toSeq

    Preconditions.checkArgument(validationResult.isEmpty, s"Invalidate calendar event: ${validationResultAsString(validationResult)}".asInstanceOf[Object])

    val calendar = new Calendar()
      .withDefaults()
      .withComponent(new VEvent(replyPropertyList))
      .withProdId(PRODID)
      .withProperty(ImmutableMethod.REPLY)

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
      }).get.getFluentTarget.asInstanceOf[Attendee]

  private def validationResultAsString(input: Seq[ValidationResult]): String =
    input.flatMap(_.getEntries.asScala.toSeq).map(error => s"${error.getContext} : ${error.getMessage}").mkString(";")
}
