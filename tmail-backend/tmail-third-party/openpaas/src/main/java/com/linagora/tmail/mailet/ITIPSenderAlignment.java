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
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.james.core.MailAddress;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.SentBy;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Organizer;

/**
 * Whether the sender of the mail carrying an ITIP message may send it: esn-sabre trusts the sender it is given, so
 * every caller sending an ITIP message to it on behalf of a mail checks it first.
 *
 * A REPLY must come from one of its attendees, any other method from the organizer. SENT-BY delegates of the same
 * domain are accepted, see {@link CalendarUserAddresses#actingAddresses}.
 */
public final class ITIPSenderAlignment {
    private ITIPSenderAlignment() {
    }

    public static boolean isSatisfied(AlignmentMode alignmentMode, MailAddress sender, Calendar calendar) {
        if (alignmentMode == AlignmentMode.NONE) {
            return true;
        }
        if (isReply(calendar)) {
            return everyVEventMatches(calendar, event -> event.getProperties(Property.ATTENDEE).stream()
                .map(attendee -> (Attendee) attendee)
                .flatMap(attendee -> CalendarUserAddresses.actingAddresses(attendee, attendee.getCalAddress()))
                .anyMatch(attendeeAddress -> isSenderAlignedWith(alignmentMode, sender, attendeeAddress)));
        }
        return everyVEventMatches(calendar, event -> Optional.ofNullable(event.getOrganizer())
            .stream()
            .flatMap(organizer -> CalendarUserAddresses.actingAddresses(organizer, organizer.getCalAddress()))
            .anyMatch(organizerAddress -> isSenderAlignedWith(alignmentMode, sender, organizerAddress)));
    }

    /**
     * The organizer of every VEVENT of the payload, when they all name the same one.
     */
    public static Optional<MailAddress> singleOrganizer(Calendar calendar) {
        List<Optional<MailAddress>> organizers = vEvents(calendar).stream()
            .map(event -> Optional.ofNullable(event.getOrganizer())
                .flatMap(organizer -> CalendarUserAddresses.toMailAddress(organizer.getCalAddress())))
            .distinct()
            .toList();
        if (organizers.size() != 1) {
            return Optional.empty();
        }
        return organizers.getFirst();
    }

    /**
     * Records on the ORGANIZER of every VEVENT the address that actually sent the payload (RFC 5545 section 3.2.18),
     * replacing any SENT-BY the payload claimed. The given calendar is modified.
     */
    public static Calendar withOrganizerSentBy(Calendar calendar, MailAddress sender) {
        SentBy sentBy = new SentBy(URI.create("mailto:" + sender.asString()));
        vEvents(calendar).forEach(event -> Optional.ofNullable(event.getOrganizer())
            .ifPresent(organizer -> event.replace((Organizer) organizer.replace(sentBy))));
        return calendar;
    }

    private static List<VEvent> vEvents(Calendar calendar) {
        return calendar.getComponents(Component.VEVENT).stream()
            .filter(VEvent.class::isInstance)
            .map(VEvent.class::cast)
            .toList();
    }

    /**
     * The alignment applies to the whole ITIP payload: every VEVENT must satisfy it, otherwise a sender could
     * smuggle VEVENTs it is not entitled to send alongside a single aligned one. A payload without VEVENT is
     * rejected.
     */
    private static boolean everyVEventMatches(Calendar calendar, Predicate<VEvent> alignment) {
        List<VEvent> events = vEvents(calendar);
        return !events.isEmpty() && events.stream().allMatch(alignment);
    }

    private static boolean isSenderAlignedWith(AlignmentMode alignmentMode, MailAddress sender, MailAddress address) {
        return switch (alignmentMode) {
            case STRICT -> sender.equals(address);
            case SAME_DOMAIN -> sender.getDomain().equals(address.getDomain());
            case NONE -> true;
        };
    }

    private static boolean isReply(Calendar calendar) {
        return calendar.getProperty(Property.METHOD)
            .map(Property::getValue)
            .map("REPLY"::equalsIgnoreCase)
            .orElse(false);
    }
}
