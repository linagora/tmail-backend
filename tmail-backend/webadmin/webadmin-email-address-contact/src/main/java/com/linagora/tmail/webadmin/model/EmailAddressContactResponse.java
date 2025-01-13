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

package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.linagora.tmail.james.jmap.contact.EmailAddressContact;

public record EmailAddressContactResponse(String id, String emailAddress, Optional<String> firstname,
                                          Optional<String> surname) {
    public static EmailAddressContactResponse from(EmailAddressContact emailAddressContact) {
        return new EmailAddressContactResponse(
            emailAddressContact.id().toString(),
            emailAddressContact.fields().address().asString(),
            nameOrEmpty(emailAddressContact.fields().firstname()),
            nameOrEmpty(emailAddressContact.fields().surname()));
    }

    private static Optional<String> nameOrEmpty(String name) {
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

}
