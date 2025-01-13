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

package com.linagora.tmail.contact;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.linagora.tmail.james.jmap.contact.ContactFields;

@JsonDeserialize(using = JCardObjectDeserializer.class)
public record JCardObject(Optional<String> fnOpt, List<MailAddress> mailAddresses) {
    public static final Logger LOGGER = LoggerFactory.getLogger(JCardObject.class);

    public JCardObject {
        Preconditions.checkNotNull(fnOpt);
        Preconditions.checkNotNull(mailAddresses);
    }

    /**
     * Purpose: To specify the formatted text corresponding to the name of
     * the object the vCard represents.
     * <p>
     * Example: Mr. John Q. Public\, Esq.
     */
    @Override
    public Optional<String> fnOpt() {
        return fnOpt;
    }

    /**
     * Purpose: To specify the electronic mail addresses for communication
     * with the object the vCard represents.
     * <p>
     * Example: ["jane_doe_at_work@example.com", "jane_doe_at_home@example.com"]
     */
    public List<MailAddress> mailAddresses() {
        return mailAddresses;
    }

    public List<ContactFields> asContactFields() {
        Optional<String> contactFullnameOpt = fnOpt();

        return mailAddresses()
            .stream()
            .map(address -> new ContactFields(address, contactFullnameOpt.orElse(""), ""))
            .toList();
    }
}
