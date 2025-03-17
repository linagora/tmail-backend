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
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
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
        if (Method.VALUE_CANCEL.equals(calendar.getMethod().getValue())) {
            return deleteDavCalendar(davUser, eventUid);
        } else {
            return davClient.getCalendarObject(davUser, eventUid)
                .flatMap(davCalendarObject -> updateDavCalendar(davCalendarObject, calendar, davUser))
                .switchIfEmpty(createDavCalendar(calendar, davUser));
        }
    }

    private Mono<Void> updateDavCalendar(DavCalendarObject davCalendarObject, Calendar calendar, DavUser davUser) {
        // TODO Rewrite this method to handle case the calendar event is already present
        return Mono.error(new RuntimeException("Calendar event already exists"));
    }

    private Mono<Void> createDavCalendar(Calendar calendar, DavUser davUser) {
        Calendar newCalendar = calendar.removeIf(property -> Property.METHOD.equals(property.getName()));
        return davClient.createCalendar(davUser.username(),
            URI.create(CALENDAR_PATH + davUser.userId() + "/" + davUser.userId() + "/" + calendar.getUid().getValue() + ".ics"),
            newCalendar);
    }

    private Mono<Void> deleteDavCalendar(DavUser davUser, EventUid eventUid) {
        return davClient.deleteCalendar(davUser.username(),
            URI.create(CALENDAR_PATH + davUser.userId() + "/" + davUser.userId() + "/" + eventUid.value() + ".ics"));
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
