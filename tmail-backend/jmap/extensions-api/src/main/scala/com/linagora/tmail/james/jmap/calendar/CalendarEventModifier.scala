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
import java.time.{DateTimeException, Instant, ZoneId, ZonedDateTime}
import java.util
import java.util.function.Consumer

import com.google.common.base.Preconditions.{checkArgument => require}
import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier._
import com.linagora.tmail.james.jmap.model.{CalendarAttendeeMailTo, CalendarEndField, CalendarLocationField, CalendarOrganizerField, CalendarStartField, CalendarTimeZoneField, VEventTemporalUtil}
import net.fortuna.ical4j.data.DefaultComponentFactorySupplier
import net.fortuna.ical4j.model.component.{CalendarComponent, VEvent}
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.property.immutable.ImmutableMethod
import net.fortuna.ical4j.model.property.{Attendee, DateProperty, DtEnd, DtStamp, DtStart, Location, RecurrenceId, Sequence, XProperty}
import net.fortuna.ical4j.model.{Calendar, Component, ComponentContainer, ComponentFactory, ComponentList, Parameter, Period, Property, PropertyList, RelationshipPropertyModifiers}
import org.apache.james.core.Username

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

trait CalendarEventUpdatePatch {

  /**
   * Return true if the patch has been applied
   */
  def apply(vEvent: VEvent): Boolean

}

case class CalendarEventTimingUpdatePatch(newStartDate: ZonedDateTime, newEndDate: ZonedDateTime) extends CalendarEventUpdatePatch {

  override def apply(vEvent: VEvent): Boolean = {
    require(!newEndDate.isBefore(newStartDate), "The end date must be after the start date".asInstanceOf[Object])

    // DTSTART, DTSTAMP is mandatory as per RFC 5545
    val zoneIdEvent: ZoneId = CalendarTimeZoneField.getZoneIdFromStartDate(vEvent)
      .getOrElse(VEventTemporalUtil.getAlternativeZoneId(vEvent))

    val startDateWithZone: ZonedDateTime = newStartDate.withZoneSameInstant(zoneIdEvent)
    val endDateWithZone: ZonedDateTime = newEndDate.withZoneSameInstant(zoneIdEvent)

    val originalStartDate: Option[ZonedDateTime] = CalendarStartField.from(vEvent).map(_.value)
    val originalEndDate: Option[ZonedDateTime] = CalendarEndField.from(vEvent).map(_.value)

    if (originalStartDate.exists(_.isEqual(startDateWithZone)) && originalEndDate.exists(_.isEqual(endDateWithZone))) {
      return false
    }

    Option(vEvent.getDateTimeStart[Temporal]())
      .map(_.setDate(startDateWithZone))
      .getOrElse(vEvent.addProperty(new DtStart[Temporal](startDateWithZone)))

    Option(vEvent.getDateTimeEnd[Temporal]())
      .map(_.setDate(endDateWithZone))
      .getOrElse {
        // RFC 5545 - Section 3.6.1 - The DTEND property and DURATION property MUST NOT occur in the same VEVENT
        vEvent.removeProperty(Property.DURATION)
        vEvent.addProperty(new DtEnd(endDateWithZone))
      }
    true
  }
}

case class LocationUpdatePatch(newLocation: String) extends CalendarEventUpdatePatch {
  override def apply(vEvent: VEvent): Boolean =
    CalendarLocationField.from(vEvent).map(_.value) match {
      case Some(originalLocation) if originalLocation.equals(newLocation) => false
      case Some(_) => vEvent.getLocation.setValue(newLocation); true
      case None => vEvent.addProperty(new Location(newLocation)); true
    }
}

case class AttendeeUpdatePatch(attendeeList: Seq[Attendee]) extends CalendarEventUpdatePatch {
  override def apply(vEvent: VEvent): Boolean = {
    val originalAttendees: Seq[String] = vEvent.getAttendees.asScala.toSeq
      .flatMap(_.getIdentity)

    val newAttendees: Seq[Property] = attendeeList
      .flatMap(attendee => attendee.getIdentity.filterNot(originalAttendees.contains)
        .map(_ => attendee.copy()))

    if (newAttendees.nonEmpty) {
      vEvent.addProperty(newAttendees)
    }
    newAttendees.nonEmpty
  }
}

case class AttendeePartStatusUpdatePatch(targetAttendeeEmail: String, status: PartStat) extends CalendarEventUpdatePatch {
  override def apply(vEvent: VEvent): Boolean =
    vEvent.getAttendees.asScala
      .find(_.getCalAddress.toASCIIString.equalsIgnoreCase(s"mailto:$targetAttendeeEmail"))
      .map { attendee =>
        attendee.getParameter[PartStat](Parameter.PARTSTAT).toScala match {
          case Some(currentPartStat) if currentPartStat.getValue.equals(status.getValue) => false
          case _ => vEvent.`with`(RelationshipPropertyModifiers.ATTENDEE, attendee.replace(status)); true
        }
      }
      .getOrElse(throw new IllegalArgumentException(s"The attendee is not found in the event. $targetAttendeeEmail"))
}

case class OrganizerValidator(requestUser: String) extends Consumer[Calendar] {
  override def accept(calendar: Calendar): Unit = {
    val vEvent = calendar.getFirstVEvent
    val organizerOpt: Option[String] = CalendarOrganizerField.from(vEvent)
      .flatMap(_.asMailAddressString())
    require(organizerOpt.isDefined, s"Can not update event, the organizer is not defined".asInstanceOf[Object])
    require(organizerOpt.get.equals(requestUser), s"Can not update event, the organizer is '$organizerOpt' and the user is '$requestUser'".asInstanceOf[Object])
  }
}

case class CalendarEventModifier(patches: Seq[CalendarEventUpdatePatch],
                                 recurrenceId: Option[RecurrenceId[Temporal]],
                                 validator: Consumer[Calendar] = (_: Calendar) => {}) {

  def apply(calendar: Calendar): Calendar =
    try {
      applyPatches(calendar)
    } catch {
      // ical4j parses date values lazily: an unparsable date or an unknown time zone surfaces as a DateTimeException
      // thrown while copying or validating the calendar rather than as a validation error
      case e: DateTimeException => throw new IllegalArgumentException(s"Invalidate calendar event: ${e.getMessage}", e)
    }

  private def applyPatches(calendar: Calendar): Calendar = {
    val newCalendar = deepCopy(calendar)
    validator.accept(newCalendar)

    val vEVentNeedToUpdate: VEvent = recurrenceId.map(findVEventByRecurrenceId(newCalendar, _))
      .getOrElse(newCalendar.getFirstVEvent)

    if (!patches.exists(_.apply(vEVentNeedToUpdate))) throw NoUpdateRequiredException()
    markEventAsModified(vEVentNeedToUpdate)
    validateAfterUpdate(newCalendar)
    newCalendar
  }

  /**
   * The VEVENT this modifier acts upon: the patched one, or, when the targeted override is missing,
   * the VEVENT it would be copied from. Lets callers inspect it (eg its STATUS) before applying the modifier.
   */
  def targetedVEvent(calendar: Calendar): Option[VEvent] =
    recurrenceId.flatMap(findOverride(calendar, _))
      .orElse(calendar.getComponents[VEvent](Component.VEVENT).asScala.headOption)

  private def findOverride(calendar: Calendar, recurrenceId: RecurrenceId[Temporal]): Option[VEvent] =
    calendar.getComponents[VEvent](Component.VEVENT).asScala
      .find(hasRecurrenceId(_, recurrenceId))

  private def findVEventByRecurrenceId(calendar: Calendar, recurrenceId: RecurrenceId[Temporal]): VEvent =
    // An existing override is used as is: it may come without master event, eg when invited to a single occurrence
    findOverride(calendar, recurrenceId)
      .getOrElse {
        val baseVEvent = calendar.getFirstVEvent
        require(baseVEvent.getProperty(Property.RRULE).isPresent || baseVEvent.getProperty(Property.RDATE).isPresent,
          s"Can not find VEVENT with recurrenceId $recurrenceId, the event is not a recurring event".asInstanceOf[Object])
        require(Option(recurrenceId.getDate).isDefined,
          s"Can not compute the occurrence, the recurrenceId $recurrenceId has no date".asInstanceOf[Object])
        require(Option(baseVEvent.getDateTimeStart[Temporal]()).flatMap(dtStart => Option(dtStart.getDate)).isDefined,
          s"Can not compute the occurrence $recurrenceId, the recurring event has no start date".asInstanceOf[Object])

        val filteredProperties: util.List[Property] = baseVEvent.getPropertyList.getAll.asScala
          .filterNot(p => RECURRENCE_IGNORE_COPIED_PROPERTIES.contains(p.getName))
          .map(copyProperty)
          .asJava

        val preVEvent = new VEvent(new PropertyList(ImmutableList.builder()
          .addAll(filteredProperties)
          .add(recurrenceId)
          .build()))

        (for {
          period  <- baseVEvent.calculateRecurrenceSet[Temporal](new Period(recurrenceId.getDate, recurrenceId.getDate)).asScala.headOption
          alternativeZoneId  = VEventTemporalUtil.getAlternativeZoneId(baseVEvent)
          startDate <- VEventTemporalUtil.temporalToZonedDateTime(period.getStart, alternativeZoneId)
          endDate   <- VEventTemporalUtil.temporalToZonedDateTime(period.getEnd, alternativeZoneId)
        } yield CalendarEventTimingUpdatePatch(startDate, endDate).apply(preVEvent))

        calendar.add(preVEvent)
        preVEvent
      }

  private def markEventAsModified(vEvent: VEvent): Unit = {
    // Update SEQUENCE
    Option(vEvent.getSequence) match {
      case Some(sequence) => sequence.setValue((vEvent.getSequence.getSequenceNo + 1).toString)
      case None => vEvent.addProperty(MODIFIED_SEQUENCE_DEFAULT.asInstanceOf[Property])
    }

    // Update DTSTAMP
    Option(vEvent.getDateTimeStamp) match {
      case Some(dtStamp) => dtStamp.setDate(Instant.now)
      case None => vEvent.addProperty(new DtStamp(Instant.now).asInstanceOf[Property])
    }
  }

  private def validateAfterUpdate(calendar: Calendar): Unit =
    CalendarValidation.requireValid(Seq(calendar.validate()))
}

object CalendarEventModifier {
  val MODIFIED_SEQUENCE_DEFAULT: Sequence = new Sequence("1")
  val RECURRENCE_IGNORE_COPIED_PROPERTIES: Set[String] = Set(Property.RRULE, Property.RDATE, Property.CREATED)
  private val COMPONENT_FACTORIES: Seq[ComponentFactory[_ <: Component]] = new DefaultComponentFactorySupplier().get().asScala.toSeq

  /**
   * Deep copy a calendar by structurally rebuilding it from copied components and properties.
   *
   * `Calendar.copy()` relies on `Property.copy()`, which fails with
   * `UnsupportedOperationException: Factory not supported for custom properties`
   * whenever the event carries custom `X-` properties (see ical4j `XProperty.newFactory`).
   *
   * We reproduce `Calendar.copy()` semantics but route every property through `copyProperty`,
   * which reconstructs `X-` properties instead of calling their unsupported `copy()`. Copying
   * property-by-property (rather than re-parsing the textual representation) is important:
   * re-parsing would rebind date/time properties to the calendar's embedded `VTIMEZONE`
   * definitions, whereas `copy()` keeps resolving their `TZID` against the IANA registry.
   */
  def deepCopy(calendar: Calendar): Calendar = {
    val properties = new PropertyList(calendar.getPropertyList.getAll.asScala.map(copyProperty).asJava)
    val components = new ComponentList(calendar.getComponents[CalendarComponent].asScala.map(copyComponent).asJava)
    new Calendar(properties, components)
  }

  /**
   * Recursively copy a component: it is rebuilt, of its own class, from `copyProperty`-copied properties
   * and recursively copied sub-components, as ical4j `Component.copy()` fails on custom `X-` properties.
   *
   * Non-standard components (which ical4j cannot copy) are kept as is: they are never patched.
   */
  def copyComponent[T <: Component](component: T): T =
    COMPONENT_FACTORIES.find(_.supports(component.getName))
      .map(factory => factory.createComponent(copyProperties(component)))
      .map(copy => withCopiedSubComponents(component, copy).asInstanceOf[T])
      .getOrElse(component)

  private def copyProperties(component: Component): PropertyList =
    new PropertyList(component.getPropertyList.getAll.asScala.map(copyProperty).asJava)

  private def withCopiedSubComponents(original: Component, copy: Component): Component = {
    (original, copy) match {
      case (originalContainer: ComponentContainer[_], copyContainer: ComponentContainer[_]) =>
        val subComponents: util.List[Component] = originalContainer.getComponentList.getAll.asScala
          .map(subComponent => copyComponent(subComponent: Component))
          .asJava
        copyContainer.asInstanceOf[ComponentContainer[Component]].setComponentList(new ComponentList(subComponents))
      case _ =>
    }
    copy
  }

  /**
   * Copy a property, handling custom `X-` properties whose `copy()` is unsupported by ical4j.
   *
   * ical4j keeps some properties with a value it cannot round-trip (eg `VERSION:2;x`, unparsable dates):
   * their `copy()` fails with an unchecked exception. The original instance can not be reused as patches
   * mutate properties in place, so such calendars are rejected as invalid.
   *
   * Date properties are evaluated upon copy, as ical4j may otherwise resolve them lazily: one with an unknown
   * TZID or an unparsable value can not be copied, nor written back, hence the calendar is rejected as invalid.
   */
  def copyProperty(property: Property): Property =
    rejectingUnevaluable(property) {
      property match {
        case xProperty: XProperty => new XProperty(xProperty.getName, xProperty.getParameterList, xProperty.getValue)
        case dateProperty: DateProperty[_] => evaluate(dateProperty).copy()
        case other => other.copy()
      }
    }

  /**
   * A requested RECURRENCE-ID with an unknown TZID or an unparsable value can not designate any occurrence:
   * the request is rejected as invalid.
   */
  private def requireEvaluable(recurrenceId: RecurrenceId[Temporal]): RecurrenceId[Temporal] =
    rejectingUnevaluable(recurrenceId)(evaluate(recurrenceId))

  private def evaluate[P <: DateProperty[_]](dateProperty: P): P = {
    dateProperty.getValue
    dateProperty.getDate
    dateProperty
  }

  private def rejectingUnevaluable[T](property: Property)(evaluation: => T): T =
    try {
      evaluation
    } catch {
      case e: RuntimeException => throw new IllegalArgumentException(s"Invalid calendar property ${property.getName}", e)
    }

  /**
   * ical4j compares RECURRENCE-IDs by their formatted values, resolving their TZID lazily: a RECURRENCE-ID
   * with an unknown TZID or an unparsable value cannot be evaluated, and is considered as not matching.
   */
  def hasRecurrenceId(vEvent: VEvent, recurrenceId: RecurrenceId[Temporal]): Boolean =
    try {
      recurrenceId == vEvent.getRecurrenceId[Temporal]
    } catch {
      case _: DateTimeException | _: NullPointerException => false
    }

  def of(patch: CalendarEventUpdatePatch): CalendarEventModifier =
    CalendarEventModifier(Seq(patch), None)

  def of(counterCalendar: Calendar, userRequest: Username): CalendarEventModifier = {
    require(ImmutableMethod.COUNTER.equals(counterCalendar.getMethod), s"The calendar must have COUNTER as a method, but got ${counterCalendar.getMethod.getValue}".asInstanceOf[Object])

    val vEvent = counterCalendar.getFirstVEvent
    val proposedStartDate: ZonedDateTime = CalendarStartField.from(vEvent).map(_.value)
      .getOrElse(throw new IllegalArgumentException("The calendar file must contain a DTSTART property"))

    val proposedEndDate: ZonedDateTime = CalendarEndField.from(vEvent).map(_.value)
      .getOrElse(throw new IllegalArgumentException("The calendar file must contain a DTEND or DURATION property"))

    val patches: Seq[CalendarEventUpdatePatch] = Seq(
      Some(CalendarEventTimingUpdatePatch(proposedStartDate, proposedEndDate)),
      CalendarLocationField.from(vEvent).map(_.value).map(LocationUpdatePatch),
      Option(vEvent.getAttendees.asScala.toSeq).map(AttendeeUpdatePatch)).flatten

    CalendarEventModifier(patches = patches,
      recurrenceId = Option(vEvent.getRecurrenceId[Temporal]).map(requireEvaluable),
      validator = OrganizerValidator(userRequest.asString()))
  }

  def withPartStat(targetAttendeeEmail: String,
                   status: PartStat,
                   requestCalendar: Calendar): CalendarEventModifier =
    withPartStat(targetAttendeeEmail = targetAttendeeEmail,
      status = status,
      recurrenceId = Option(requestCalendar.getFirstVEvent.getRecurrenceId[Temporal]))

  def withPartStat(targetAttendeeEmail: String,
                   status: PartStat,
                   recurrenceId: Option[RecurrenceId[Temporal]]): CalendarEventModifier = {
    val evaluableRecurrenceId = recurrenceId.map(requireEvaluable)

    val validator: Consumer[Calendar] = calendar =>
      evaluableRecurrenceId.foreach { rid =>
        calendar.getComponents[VEvent](Component.VEVENT).asScala
          .find(hasRecurrenceId(_, rid))
          .getOrElse(throw new IllegalArgumentException(s"Cannot find VEVENT with recurrenceId $rid"))
      }

    CalendarEventModifier(Seq(AttendeePartStatusUpdatePatch(targetAttendeeEmail, status)), evaluableRecurrenceId, validator)
  }

  case class NoUpdateRequiredException() extends RuntimeException

  implicit class ImplicitCalendar(calendar: Calendar) {
    def getFirstVEvent: VEvent =
      calendar.getComponents[VEvent](Component.VEVENT).asScala.toSeq.headOption match {
        case Some(vEvent) => vEvent
        case _ => throw new IllegalArgumentException("The calendar file must contain at least one VEVENT component")
      }
  }

  implicit class ImplicitVEvent(vEvent: VEvent) {
    def addProperty(property: Property): Unit =
      vEvent.setPropertyList(new PropertyList(ImmutableList.builder()
        .addAll(vEvent.getProperties[Property])
        .add(property)
        .build()))

    def addProperty(property: Seq[Property]): Unit =
      vEvent.setPropertyList(new PropertyList(ImmutableList.builder()
        .addAll(vEvent.getProperties[Property])
        .addAll(property.asJava)
        .build()))

    def removeProperty(properties: String*): Unit = {
      val propertyList: util.List[Property] = vEvent.getPropertyList.removeIf(p => properties.contains(p.getName)).getAll
      vEvent.setPropertyList(new PropertyList(propertyList))
    }
  }

  implicit class ImplicitAttendee(attendee: Attendee) {
    def getIdentity: Option[String] =
      CalendarAttendeeMailTo.from(attendee).map(_.value.asString())
  }
}
