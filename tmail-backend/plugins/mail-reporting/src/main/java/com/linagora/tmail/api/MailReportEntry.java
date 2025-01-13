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

package com.linagora.tmail.api;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;

public record MailReportEntry(Kind kind,
                              String subject,
                              MaybeSender sender,
                              MailAddress recipient,
                              Instant date,
                              long size) {
    public enum Kind {
        Sent("SENT"),
        Received("RECEIVED");

        public static Optional<Kind> parse(String value) {
            return Stream.of(Kind.values())
                .filter(kind -> kind.asString().equals(value))
                .findFirst();
        }

        private final String value;

        Kind(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }
    }

}
