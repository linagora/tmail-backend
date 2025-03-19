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

package com.linagora.tmail.james.jmap.calendar

import java.time.temporal.Temporal
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util
import java.util.function.Consumer

import com.google.common.base.Preconditions.{checkArgument => require}
import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.jmap.model.{CalendarEventParsed, CalendarTimeZoneField, VEventTemporalUtil}
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.{DtEnd, Sequence}
import net.fortuna.ical4j.model.{Calendar, Component, Property, PropertyList}
import net.fortuna.ical4j.validate.ValidationResult

import scala.jdk.CollectionConverters._

object CalendarEventModifier {
  val MODIFIED_SEQUENCE_DEFAULT: Sequence = new Sequence("1")

  case class NoUpdateRequiredException(message: String) extends RuntimeException(message)

  def modifyEventTiming(originalCalendar: Calendar,
                        newStartDate: ZonedDateTime,
                        newEndDate: ZonedDateTime,
                        validator: Consumer[VEvent] = (_: VEvent) => {}): Calendar = {
    validateBeforeUpdate(originalCalendar, newStartDate, newEndDate)

    val newCalendar = originalCalendar.copy()
    val vEvent = newCalendar.getComponent[VEvent](Component.VEVENT).get()
    validator.accept(vEvent)
    updateEvent(vEvent, newStartDate, newEndDate)

    validateAfterUpdate(newCalendar)
    newCalendar
  }

  private def validateBeforeUpdate(originalCalendar: Calendar,
                          newStartDate: ZonedDateTime,
                          newEndDate: ZonedDateTime): Unit = {
    require(!newEndDate.isBefore(newStartDate), "The end date must be after the start date".asInstanceOf[Object])
    val (originalStartDate: Option[ZonedDateTime], originalEndDate: Option[ZonedDateTime]) = parseOriginalEventDate(originalCalendar)

    if (originalStartDate.exists(_.isEqual(newStartDate)) && originalEndDate.exists(_.isEqual(newEndDate))) {
      throw NoUpdateRequiredException("No changes detected in startDate or endDate. Update is not necessary.")
    }
  }

  private def validateAfterUpdate(calendar: Calendar): Unit = {
    val validationResult: ValidationResult = calendar.validate()
    require(!validationResult.hasErrors,
      s"Invalidate calendar event: ${validationResult.getEntries.asScala.toSeq.map(error => s"${error.getContext} : ${error.getMessage}").mkString(";")}".asInstanceOf[Object])
  }

  /**
   * return the original start date and end date
   */
  private def parseOriginalEventDate(counterCalendar: Calendar): (Option[ZonedDateTime], Option[ZonedDateTime]) =
    CalendarEventParsed.from(counterCalendar) match {
      case Seq(singleEvent) => (singleEvent.start.map(_.value), singleEvent.end.map(_.value))
      case _ => throw new IllegalArgumentException("The calendar file must contain exactly one VEVENT component")
    }

  private def updateEvent(vEvent: VEvent, startDate: ZonedDateTime, endDate: ZonedDateTime): Unit = {
    // DTSTART, DTSTAMP is mandatory as per RFC 5545

    val zoneIdEvent: ZoneId = CalendarTimeZoneField.getZoneIdFromStartDate(vEvent)
      .getOrElse(VEventTemporalUtil.getAlternativeZoneId(vEvent))

    val startDateWithZone: ZonedDateTime = startDate.withZoneSameInstant(zoneIdEvent)
    val endDateWithZone: ZonedDateTime = endDate.withZoneSameInstant(zoneIdEvent)

    vEvent.getDateTimeStart[Temporal]().setDate(startDateWithZone)
    vEvent.getDateTimeStamp.setDate(Instant.now)

    // Update DTEND or remove DURATION if necessary
    Option(vEvent.getDateTimeEnd[Temporal]()) match {
      case Some(dateTimeEnd) => dateTimeEnd.setDate(endDateWithZone)
      case None =>
        // RFC 5545 - Section 3.6.1 - The DTEND property and DURATION property MUST NOT occur in the same VEVENT
        val propertyList: util.List[Property] = vEvent.getPropertyList.removeIf(_.getName == Property.DURATION).getAll
        vEvent.setPropertyList(new PropertyList(ImmutableList.builder()
          .addAll(propertyList)
          .add(new DtEnd(endDateWithZone))
          .build())
        )
    }

    // Update the sequence number & last modified date
    Option(vEvent.getSequence) match {
      case Some(sequence) => sequence.setValue((vEvent.getSequence.getSequenceNo + 1).toString)
      case None =>
        vEvent.setPropertyList(new PropertyList(ImmutableList.builder()
          .addAll(vEvent.getProperties[Property])
          .add(MODIFIED_SEQUENCE_DEFAULT.asInstanceOf[Property])
          .build()))
    }
  }
}
