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

import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavUser;
import com.linagora.tmail.dav.DavUserProvider;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Organizer;
import reactor.core.publisher.Mono;

public class CalDavCollect extends GenericMailet {
    public static final String SOURCE_ATTRIBUTE_NAME = "source";
    public static final String DEFAULT_SOURCE_ATTRIBUTE_NAME = "icalendarJson";

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavCollect.class);
    private static final Class<Map<String, AttributeValue<byte[]>>> MAP_STRING_JSON_BYTES_CLASS = (Class<Map<String, AttributeValue<byte[]>>>) (Object) Map.class;

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
            AttributeUtils.getValueAndCastFromMail(mail, sourceAttributeName, MAP_STRING_JSON_BYTES_CLASS)
                .ifPresent(jsons -> jsons.values()
                    .stream()
                    .map(AttributeValue::getValue)
                    .toList()
                    .forEach(json -> handleCalendarInMail(json, mail)));
        } catch (ClassCastException e) {
            LOGGER.error("Attribute {} is not Map<String, AttributeValue<byte[]> in mail {}", sourceAttributeName, mail.getName(), e);
        }
    }

    private void handleCalendarInMail(byte[] json, Mail mail) {
        JsonNode jsonNode = convertToJson(json, mail.getName());

        String icalContent = jsonNode.path("ical").asText();
        String recipient = jsonNode.path("recipient").asText();

        if (!icalContent.isEmpty() && !recipient.isEmpty()) {
            try {
                MailAddress mailAddress = new MailAddress(recipient);
                Calendar calendar = parseICalString(icalContent);

                if (concernsRecipient(mailAddress, calendar)) {
                    davUserProvider.provide(Username.of(mailAddress.asString()))
                        .flatMap(davUser -> synchronizeWithDavServer(json, davUser))
                        .block();
                }
            } catch (Exception e) {
                LOGGER.error("Error while handling calendar in mail {} with recipient {}", mail.getName(), recipient, e);
            }
        }
    }

    private Mono<Void> synchronizeWithDavServer(byte[] json, DavUser davUser) {
        return davClient.sendITIPRequest(davUser.username(),
            URI.create(CALENDAR_PATH + davUser.userId()),
            json);
    }

    private JsonNode convertToJson(byte[] json, String mailName) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonString = new String(json, StandardCharsets.UTF_8);
            return mapper.readTree(jsonString);
        } catch (Exception e) {
            LOGGER.error("Error while handling calendar in mail {}", mailName, e);
            return mapper.createObjectNode();
        }
    }

    private Calendar parseICalString(String icsContent) throws Exception {
        StringReader reader = new StringReader(icsContent);
        return new CalendarBuilder().build(reader);
    }

    private boolean concernsRecipient(MailAddress mailAddress, Calendar calendar) {
        return calendar.getComponents(Component.VEVENT)
            .stream()
            .filter(VEvent.class::isInstance)
            .map(VEvent.class::cast)
            .anyMatch(event -> isOrganizer(mailAddress, event) || isAttendee(mailAddress, event));
    }

    private static boolean isAttendee(MailAddress mailAddress, VEvent event) {
        return event.getProperties(Property.ATTENDEE)
            .stream()
            .map(attendee -> (Attendee) attendee)
            .map(Attendee::getCalAddress)
            .map(URI::getSchemeSpecificPart)
            .map(Throwing.function(MailAddress::new))
            .anyMatch(mailAddress::equals);
    }

    private static Boolean isOrganizer(MailAddress mailAddress, VEvent event) {
        return Optional.ofNullable(event.getOrganizer())
            .map(Organizer::getCalAddress)
            .map(URI::getSchemeSpecificPart)
            .map(Throwing.function(MailAddress::new))
            .map(mailAddress::equals)
            .orElse(false);
    }
}
