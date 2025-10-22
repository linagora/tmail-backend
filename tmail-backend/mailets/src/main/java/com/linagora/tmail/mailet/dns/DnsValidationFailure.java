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

package com.linagora.tmail.mailet.dns;

/**
 * Represents DNS validation failures for email authentication records.
 */
public sealed interface DnsValidationFailure permits
    DnsValidationFailure.DkimValidationFailure,
    DnsValidationFailure.SpfValidationFailure,
    DnsValidationFailure.DmarcValidationFailure {

    String message();

    /**
     * DKIM DNS validation failure.
     */
    record DkimValidationFailure(String message) implements DnsValidationFailure {}

    /**
     * SPF DNS validation failure.
     */
    record SpfValidationFailure(String message) implements DnsValidationFailure {}

    /**
     * DMARC DNS validation failure.
     */
    record DmarcValidationFailure(String message) implements DnsValidationFailure {}
}
