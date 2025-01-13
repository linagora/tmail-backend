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

package com.linagora.tmail.james.common.probe;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.utils.GuiceProbe;

import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class JmapGuiceContactAutocompleteProbe implements GuiceProbe {
    private final EmailAddressContactSearchEngine emailAddressContactSearchEngine;

    @Inject
    public JmapGuiceContactAutocompleteProbe(EmailAddressContactSearchEngine emailAddressContactSearchEngine) {
        this.emailAddressContactSearchEngine = emailAddressContactSearchEngine;
    }

    public EmailAddressContact index(AccountId accountId, ContactFields contactFields) {
        return Mono.from(emailAddressContactSearchEngine.index(accountId, contactFields)).block();
    }

    public EmailAddressContact index(Domain domain, ContactFields contactFields) {
        return Mono.from(emailAddressContactSearchEngine.index(domain, contactFields)).block();
    }

    public List<EmailAddressContact> list(AccountId accountId) {
        return Flux.from(emailAddressContactSearchEngine.list(accountId))
            .collectList()
            .block();
    }
}
