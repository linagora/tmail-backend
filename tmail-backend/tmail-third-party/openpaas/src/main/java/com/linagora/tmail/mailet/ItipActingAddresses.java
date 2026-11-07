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
import java.util.stream.Stream;

import org.apache.james.core.MailAddress;

import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;

final class ItipActingAddresses {
    private ItipActingAddresses() {
    }

    /**
     * The addresses allowed to send an ITIP message on behalf of a calendar user: its own address, and the
     * delegate designated by the SENT-BY parameter (RFC 5545 section 3.2.18), used when an assistant sends
     * the invitation for the organizer, or a reply for an attendee.
     *
     * SENT-BY is written by the sender of the payload and nothing proves the delegation: a delegate is only
     * accepted within the domain of the calendar user, otherwise anyone could act for anyone.
     */
    static Stream<MailAddress> of(Property calendarUser, URI calAddress) {
        Optional<MailAddress> calendarUserAddress = CalendarUserAddresses.toMailAddress(calAddress);
        return Stream.concat(
            calendarUserAddress.stream(),
            sentBy(calendarUser)
                .filter(delegate -> calendarUserAddress
                    .map(address -> address.getDomain().equals(delegate.getDomain()))
                    .orElse(false))
                .stream());
    }

    private static Optional<MailAddress> sentBy(Property calendarUser) {
        return calendarUser.getParameter(Parameter.SENT_BY)
            .map(Parameter::getValue)
            .flatMap(CalendarUserAddresses::toMailAddress);
    }
}
