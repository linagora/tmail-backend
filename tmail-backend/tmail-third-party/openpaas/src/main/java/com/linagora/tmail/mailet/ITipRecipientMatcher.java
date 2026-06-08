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

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.Mappings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Organizer;

/**
 * Matches a mail recipient against the ORGANIZER / ATTENDEE addresses of an iCalendar payload.
 *
 * The recipient carried by the mail has usually already been rewritten by the
 * {@code RecipientRewriteTable} mailet (e.g. an alias normalized to the primary address), whereas
 * the addresses inside the ICS still reference whatever address the organizer originally invited
 * (typically the alias). A plain equality check therefore fails for aliased recipients.
 *
 * The match is performed in two steps: first an exact comparison, then a fallback resolving the ICS
 * address through the {@link RecipientRewriteTable} and checking whether the recipient is one of the
 * resolved addresses.
 */
public class ITipRecipientMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ITipRecipientMatcher.class);

    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    public ITipRecipientMatcher(RecipientRewriteTable recipientRewriteTable) {
        this.recipientRewriteTable = recipientRewriteTable;
    }

    public boolean concernsRecipient(MailAddress recipient, Calendar calendar) {
        return vEvents(calendar)
            .anyMatch(event -> isOrganizer(recipient, event) || isAttendee(recipient, event));
    }

    public boolean isExplicitAttendee(MailAddress recipient, Calendar calendar) {
        return vEvents(calendar)
            .anyMatch(event -> isAttendee(recipient, event));
    }

    private Stream<VEvent> vEvents(Calendar calendar) {
        return calendar.getComponents(Component.VEVENT)
            .stream()
            .filter(VEvent.class::isInstance)
            .map(VEvent.class::cast);
    }

    private boolean isAttendee(MailAddress recipient, VEvent event) {
        return event.getProperties(Property.ATTENDEE)
            .stream()
            .map(Attendee.class::cast)
            .map(Attendee::getCalAddress)
            .map(URI::getSchemeSpecificPart)
            .flatMap(address -> toMailAddress(address).stream())
            .anyMatch(attendee -> matches(recipient, attendee));
    }

    private boolean isOrganizer(MailAddress recipient, VEvent event) {
        return Optional.ofNullable(event.getOrganizer())
            .map(Organizer::getCalAddress)
            .map(URI::getSchemeSpecificPart)
            .flatMap(this::toMailAddress)
            .map(organizer -> matches(recipient, organizer))
            .orElse(false);
    }

    private boolean matches(MailAddress recipient, MailAddress calendarAddress) {
        return recipient.equals(calendarAddress)
            || resolveAddresses(calendarAddress).contains(recipient);
    }

    private Set<MailAddress> resolveAddresses(MailAddress calendarAddress) {
        try {
            Mappings mappings = recipientRewriteTable.getResolvedMappings(calendarAddress.getLocalPart(), calendarAddress.getDomain());
            if (mappings == null || mappings.isEmpty()) {
                return ImmutableSet.of();
            }
            return mappings.asStream()
                .map(mapping -> mapping.appendDomainIfNone(calendarAddress::getDomain))
                .map(Mapping::asMailAddress)
                .flatMap(Optional::stream)
                .collect(ImmutableSet.toImmutableSet());
        } catch (Exception e) {
            LOGGER.warn("Error while resolving RRT mappings for ICS address {}", calendarAddress.asString(), e);
            return ImmutableSet.of();
        }
    }

    private Optional<MailAddress> toMailAddress(String address) {
        try {
            return Optional.of(new MailAddress(address));
        } catch (Exception e) {
            LOGGER.info("Skipping invalid mail address in iCal payload: {}", address);
            return Optional.empty();
        }
    }
}
