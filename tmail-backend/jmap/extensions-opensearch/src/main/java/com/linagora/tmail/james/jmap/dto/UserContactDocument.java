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

package com.linagora.tmail.james.jmap.dto;

import java.util.UUID;

import org.apache.james.jmap.api.model.AccountId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;

public class UserContactDocument {
    private final String accountId;
    private final UUID contactId;
    private final String email;
    private final String firstname;
    private final String surname;

    public UserContactDocument(AccountId accountId, EmailAddressContact contact) {
        this.accountId = accountId.getIdentifier();
        this.contactId = contact.id();
        this.email = contact.fields().address().asString();
        this.firstname = contact.fields().firstname();
        this.surname = contact.fields().surname();
    }

    @JsonProperty("accountId")
    public String getAccountId() {
        return accountId;
    }

    @JsonProperty("contactId")
    public UUID getContactId() {
        return contactId;
    }

    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

    @JsonProperty("firstname")
    public String getFirstname() {
        return firstname;
    }

    @JsonProperty("surname")
    public String getSurname() {
        return surname;
    }
}
