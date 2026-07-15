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

package com.linagora.tmail.webadmin.mailinglist;

import org.apache.james.core.MailAddress;

/**
 * Base class for the failures the mailing list management (write) routes can surface. Each subclass maps to a
 * dedicated HTTP status in {@link MailingListManagementRoutes}, keeping the repository layer free of HTTP concerns.
 */
public abstract class MailingListManagementException extends RuntimeException {
    /**
     * The targeted mailing list already exists. Translated to a {@code 409 Conflict}.
     */
    public static class AlreadyExists extends MailingListManagementException {
        public AlreadyExists(MailAddress address) {
            super(String.format("The mailing list '%s' already exists", address.asString()));
        }
    }

    /**
     * The targeted mailing list does not exist. Translated to a {@code 404 Not Found}.
     */
    public static class NotFound extends MailingListManagementException {
        public NotFound(MailAddress address) {
            super(String.format("The mailing list '%s' does not exist", address.asString()));
        }
    }

    /**
     * A member/owner address does not resolve to any user entry. Translated to a {@code 400 Bad Request}.
     */
    public static class UnknownMember extends MailingListManagementException {
        public UnknownMember(MailAddress address) {
            super(String.format("The address '%s' does not resolve to any user", address.asString()));
        }
    }

    /**
     * The requested write is not supported for this LDAP schema (typically owner management, or any write against an
     * OBM {@code obmGroup} list). Translated to a {@code 409 Conflict}.
     */
    public static class WriteNotSupported extends MailingListManagementException {
        public WriteNotSupported(String message) {
            super(message);
        }
    }

    /**
     * The write would leave a {@code groupOfNames} list without any member, which the schema forbids. Translated to a
     * {@code 409 Conflict}.
     */
    public static class EmptyList extends MailingListManagementException {
        public EmptyList(MailAddress address) {
            super(String.format("A mailing list cannot be left without any member (list '%s')", address.asString()));
        }
    }

    protected MailingListManagementException(String message) {
        super(message);
    }
}
