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

package com.linagora.tmail.mailet;

import static com.linagora.tmail.dav.DavClient.CALENDAR_PATH;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.Username;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.dav.DavCalendarObject;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavUser;
import com.linagora.tmail.dav.DavUserProvider;
import com.linagora.tmail.dav.EventUid;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.model.property.Uid;
import reactor.core.publisher.Mono;

public class CalDavCollect extends GenericMailet {
    public static final String SOURCE_ATTRIBUTE_NAME = "source";
    public static final String DEFAULT_SOURCE_ATTRIBUTE_NAME = "calendars";

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavCollect.class);
    private static final Class<Map<String, AttributeValue<Calendar>>> MAP_STRING_CALENDAR_CLASS = (Class<Map<String, AttributeValue<Calendar>>>) (Object) Map.class;

    private final DavClient davClient;
    private final DavUserProvider davUserProvider;

    private AttributeName sourceAttributeName;

    @Inject
    public CalDavCollect(DavClient davClient, DavUserProvider davUserProvider) {
        this.davClient = davClient;
        this.davUserProvider = davUserProvider;
    }

    @Override
    public void init() throws MessagingException {
        sourceAttributeName = AttributeName.of(getInitParameter(SOURCE_ATTRIBUTE_NAME, DEFAULT_SOURCE_ATTRIBUTE_NAME));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            AttributeUtils.getValueAndCastFromMail(mail, sourceAttributeName, MAP_STRING_CALENDAR_CLASS)
                .ifPresent(calendars -> calendars.values()
                    .stream()
                    .map(AttributeValue::getValue)
                    .toList()
                    .forEach(calendar -> handleCalendarInMail(calendar, mail)));
        } catch (ClassCastException e) {
            LOGGER.error("Received a mail with {} not being an ICAL object for mail {}", sourceAttributeName, mail.getName(), e);
        }
    }

    private void handleCalendarInMail(Calendar calendar, Mail mail) {
        mail.getRecipients()
            .forEach(mailAddress -> {
                try {
                    davUserProvider.provide(Username.of(mailAddress.asString()))
                        .flatMap(davUser -> synchronizeWithDavServer(calendar, davUser))
                        .block();
                } catch (Exception e) {
                    LOGGER.error("Error while handling calendar in mail {} with recipient {}", mail.getName(), mailAddress.asString(), e);
                }
            });
    }
    
    private Mono<Void> synchronizeWithDavServer(Calendar calendar, DavUser davUser) {
        EventUid eventUid = getEventUid(calendar);
        return davClient.getCalendarObject(davUser, eventUid)
            .singleOptional()
            .flatMap(maybeDavCalendarObject -> {
                if (checkIfCalendarHasRecurrenceId(calendar)) {
                    return maybeDavCalendarObject.map(davCalendarObject -> handleAnEventInRecurringCalendar(davCalendarObject, calendar, davUser))
                        .orElse(Mono.empty());
                } else {
                    if (Method.VALUE_CANCEL.equals(calendar.getMethod().getValue())) {
                        return deleteDavCalendar(davUser, eventUid);
                    } else {
                        return maybeDavCalendarObject.map(davCalendarObject -> updateDavCalendar(davCalendarObject, calendar, davUser))
                            .orElseGet(() -> createDavCalendar(calendar, davUser));
                    }
                }
            });
    }

    private boolean checkIfCalendarHasRecurrenceId(Calendar calendar) {
        return calendar.getComponent(Component.VEVENT)
            .map(vevent -> vevent.getProperty(Property.RECURRENCE_ID).isPresent())
            .orElse(false);
    }

    private Mono<Void> createDavCalendar(Calendar calendar, DavUser davUser) {
        return davClient.createCalendar(davUser.username(),
            URI.create(CALENDAR_PATH + davUser.userId() + "/" + davUser.userId() + "/" + calendar.getUid().getValue() + ".ics"),
            calendar.removeIf(property -> Property.METHOD.equals(property.getName())));
    }

    private Mono<Void> updateDavCalendar(DavCalendarObject davCalendarObject, Calendar newCalendar, DavUser davUser) {
        if (checkIfNewCalendarIsNewVersion(newCalendar, davCalendarObject.calendarData())) {
            DavCalendarObject newDavCalendarObject = new DavCalendarObject(davCalendarObject.uri(),
                newCalendar.removeIf(property -> Property.METHOD.equals(property.getName())),
                davCalendarObject.eTag());
            return davClient.doUpdateCalendarObject(davUser.username(), newDavCalendarObject);
        } else {
            return Mono.empty();
        }
    }

    private Mono<Void> deleteDavCalendar(DavUser davUser, EventUid eventUid) {
        return davClient.deleteCalendar(davUser.username(),
            URI.create(CALENDAR_PATH + davUser.userId() + "/" + davUser.userId() + "/" + eventUid.value() + ".ics"));
    }

    private boolean checkIfNewCalendarIsNewVersion(Calendar newCalendar, Calendar currentCalendar) {
        CalendarComponent newVEvent = newCalendar.getComponent(Component.VEVENT).get();
        CalendarComponent currentVEvent = currentCalendar.getComponent(Component.VEVENT).get();
        Optional<Property> maybeNewSequence = newVEvent.getProperty(Property.SEQUENCE);
        Optional<Property> maybeCurrentSequence = currentVEvent.getProperty(Property.SEQUENCE);
        if (maybeNewSequence.isPresent() && maybeCurrentSequence.isPresent()) {
            int newSequence = Integer.parseInt(maybeNewSequence.get().getValue());
            int currentSequence = Integer.parseInt(maybeCurrentSequence.get().getValue());
            if (newSequence > currentSequence) {
                return true;
            } else if (newSequence == currentSequence) {
                return checkIfNewDtStampIsLater(newVEvent, currentVEvent);
            } else {
                return false;
            }
        } else {
            return checkIfNewDtStampIsLater(newVEvent, currentVEvent);
        }
    }

    private Mono<Void> handleAnEventInRecurringCalendar(DavCalendarObject davCalendarObject, Calendar newCalendar, DavUser davUser) {
        CalendarComponent newVEvent = newCalendar.getComponent(Component.VEVENT).get();
        Optional<CalendarComponent> maybeCurrentVEvent = getCurrentVEventContainingSameRecurrenceId(newVEvent, davCalendarObject.calendarData());
        if (Method.VALUE_CANCEL.equals(newCalendar.getMethod().getValue())) {
            return deleteVEventFromRecurringCalendar(newVEvent, maybeCurrentVEvent, davCalendarObject, davUser);
        } else {
            if (maybeCurrentVEvent.isPresent()) {
                return updateVEventInRecurringCalendar(newVEvent, maybeCurrentVEvent.get(), davCalendarObject, davUser);
            } else {
                return createVEventInRecurringCalendar(newVEvent, davCalendarObject, davUser);
            }
        }
    }

    private Mono<Void> createVEventInRecurringCalendar(CalendarComponent newVEvent, DavCalendarObject davCalendarObject, DavUser davUser) {
        Calendar currentCalendar = davCalendarObject.calendarData();
        CalendarComponent originalVEvent = getCurrentVEventContainingRRule(currentCalendar).get();
        if (checkIfNewEventComponentIsNewVersion(newVEvent, originalVEvent)) {
            currentCalendar.getComponentList().add(newVEvent);
            DavCalendarObject newDavCalendarObject = new DavCalendarObject(davCalendarObject.uri(),
                currentCalendar,
                davCalendarObject.eTag());
            return davClient.doUpdateCalendarObject(davUser.username(), newDavCalendarObject);
        } else {
            return Mono.empty();
        }
    }

    private Mono<Void> updateVEventInRecurringCalendar(CalendarComponent newVEvent, CalendarComponent currentVEvent, DavCalendarObject davCalendarObject, DavUser davUser) {
        Calendar currentCalendar = davCalendarObject.calendarData();
        if (checkIfNewEventComponentIsNewVersion(newVEvent, currentVEvent)) {
            currentCalendar.getComponentList().remove(currentVEvent);
            currentCalendar.getComponentList().add(newVEvent);
            DavCalendarObject newDavCalendarObject = new DavCalendarObject(davCalendarObject.uri(),
                currentCalendar,
                davCalendarObject.eTag());
            return davClient.doUpdateCalendarObject(davUser.username(), newDavCalendarObject);
        } else {
            return Mono.empty();
        }
    }

    private Mono<Void> deleteVEventFromRecurringCalendar(CalendarComponent deletedVEvent, Optional<CalendarComponent> maybeCurrentVEvent, DavCalendarObject davCalendarObject, DavUser davUser) {
        Calendar currentCalendar = davCalendarObject.calendarData();
        CalendarComponent originalVEvent = getCurrentVEventContainingRRule(currentCalendar).get();
        if (checkIfNewEventComponentIsNewVersion(deletedVEvent, originalVEvent)) {
            maybeCurrentVEvent.ifPresent(calendarComponent -> currentCalendar.getComponentList().remove(calendarComponent));
            originalVEvent.add(new ExDate(deletedVEvent.getProperty(Property.RECURRENCE_ID).get().getValue()));
            DavCalendarObject newDavCalendarObject = new DavCalendarObject(davCalendarObject.uri(),
                currentCalendar,
                davCalendarObject.eTag());
            return davClient.doUpdateCalendarObject(davUser.username(), newDavCalendarObject);
        } else {
            return Mono.empty();
        }
    }

    private Optional<CalendarComponent> getCurrentVEventContainingSameRecurrenceId(CalendarComponent newVEvent, Calendar currentCalendar) {
        return currentCalendar.getComponents().stream()
            .filter(component -> Component.VEVENT.equals(component.getName()))
            .filter(component -> component.getProperty(Property.RECURRENCE_ID)
                .map(recurrenceId -> recurrenceId.equals(newVEvent.getProperty(Property.RECURRENCE_ID).get())).orElse(false))
            .findAny();
    }

    private Optional<CalendarComponent> getCurrentVEventContainingRRule(Calendar currentCalendar) {
        return currentCalendar.getComponents().stream()
            .filter(component -> Component.VEVENT.equals(component.getName()))
            .filter(component -> component.getProperty(Property.RRULE).isPresent())
            .findAny();
    }

    private boolean checkIfNewEventComponentIsNewVersion(CalendarComponent newEvent, CalendarComponent currentEvent) {
        Optional<Property> newLastModified = newEvent.getProperty(Property.LAST_MODIFIED);
        Optional<Property> currentLastModified = currentEvent.getProperty(Property.LAST_MODIFIED);
        if (newLastModified.isPresent() && currentLastModified.isPresent()) {
            return newLastModified.get().compareTo(currentLastModified.get()) > 0;
        } else {
            return checkIfNewDtStampIsLater(newEvent, currentEvent);
        }
    }

    private boolean checkIfNewDtStampIsLater(CalendarComponent newVEvent, CalendarComponent currentVEvent) {
        return newVEvent.getProperty(Property.DTSTAMP).get().compareTo(currentVEvent.getProperty(Property.DTSTAMP).get()) > 0;
    }

    private EventUid getEventUid(Calendar calendar) {
        VEvent vevent = (VEvent) calendar.getComponent("VEVENT")
            .orElseThrow(() -> new RuntimeException("Failed to get VEVENT component"));
        return vevent.getUid()
            .map(Uid::getValue)
            .map(EventUid::new)
            .orElseThrow(() -> new RuntimeException("Failed to get VEVENT uid"));
    }
}
