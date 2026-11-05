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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns an iCalendar cal-address (RFC 5545 section 3.3.3) into the {@link MailAddress} it designates.
 *
 * Only the {@code mailto} scheme (case-insensitive) and scheme-less addresses are accepted. Following RFC 6068
 * section 2, the hfields after the first {@code ?} are not part of the address and are dropped, then the
 * address is percent-decoded.
 */
final class CalendarUserAddresses {
    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarUserAddresses.class);
    private static final String MAILTO_SCHEME = "mailto";
    private static final char HFIELDS_SEPARATOR = '?';

    private CalendarUserAddresses() {
    }

    static Optional<MailAddress> toMailAddress(String calAddress) {
        try {
            return toMailAddress(URI.create(calAddress));
        } catch (IllegalArgumentException e) {
            LOGGER.info("Skipping invalid calendar user address in iCal payload: {}", calAddress);
            return Optional.empty();
        }
    }

    static Optional<MailAddress> toMailAddress(URI calAddress) {
        try {
            return Optional.ofNullable(calAddress)
                .filter(CalendarUserAddresses::hasNoFragment)
                .filter(CalendarUserAddresses::isMailtoOrSchemeLess)
                .map(URI::getRawSchemeSpecificPart)
                .map(CalendarUserAddresses::withoutHfields)
                .map(CalendarUserAddresses::percentDecode)
                .flatMap(CalendarUserAddresses::toMailAddressSilently);
        } catch (IllegalArgumentException e) {
            LOGGER.info("Skipping invalid calendar user address in iCal payload: {}", calAddress);
            return Optional.empty();
        }
    }

    // RFC 6068 section 2 requires '#' to be percent-encoded in an address. URI excludes the fragment from the
    // scheme-specific part: without this check the address validated would differ from the value written in the payload
    private static boolean hasNoFragment(URI calAddress) {
        return calAddress.getRawFragment() == null;
    }

    private static boolean isMailtoOrSchemeLess(URI calAddress) {
        return calAddress.getScheme() == null || MAILTO_SCHEME.equalsIgnoreCase(calAddress.getScheme());
    }

    private static String withoutHfields(String rawAddress) {
        int hfieldsStart = rawAddress.indexOf(HFIELDS_SEPARATOR);
        if (hfieldsStart < 0) {
            return rawAddress;
        }
        return rawAddress.substring(0, hfieldsStart);
    }

    private static String percentDecode(String rawAddress) {
        // URLDecoder implements form decoding, where '+' stands for a space: protect it as it is a literal in a mailto URI
        return URLDecoder.decode(rawAddress.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static Optional<MailAddress> toMailAddressSilently(String address) {
        try {
            return Optional.of(new MailAddress(address));
        } catch (Exception e) {
            LOGGER.info("Skipping invalid mail address in iCal payload: {}", address);
            return Optional.empty();
        }
    }
}
