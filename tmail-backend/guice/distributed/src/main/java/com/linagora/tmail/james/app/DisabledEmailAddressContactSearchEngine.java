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

package com.linagora.tmail.james.app;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.jmap.api.model.AccountId;
import org.reactivestreams.Publisher;

import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;

import reactor.core.publisher.Mono;

public class DisabledEmailAddressContactSearchEngine implements EmailAddressContactSearchEngine {
    @Override
    public Publisher<EmailAddressContact> index(AccountId accountId, ContactFields fields) {
        return Mono.error(new NotImplementedException());
    }

    @Override
    public Publisher<EmailAddressContact> index(Domain domain, ContactFields fields) {
        return Mono.error(new NotImplementedException());
    }

    @Override
    public Publisher<EmailAddressContact> update(AccountId accountId, ContactFields updatedFields) {
        return Mono.error(new NotImplementedException());
    }

    @Override
    public Publisher<EmailAddressContact> update(Domain domain, ContactFields updatedFields) {
        return Mono.error(new NotImplementedException());
    }

    @Override
    public Publisher<Void> delete(AccountId accountId, MailAddress mailAddress) {
        return Mono.error(new NotImplementedException());
    }

    @Override
    public Publisher<Void> delete(Domain domain, MailAddress mailAddress) {
        return Mono.error(new NotImplementedException());
    }

    @Override
    public Publisher<EmailAddressContact> autoComplete(AccountId accountId, String part, int limit) {
        return Mono.empty();
    }

    @Override
    public Publisher<EmailAddressContact> list(AccountId accountId) {
        return Mono.empty();
    }

    @Override
    public Publisher<EmailAddressContact> list(Domain domain) {
        return Mono.empty();
    }

    @Override
    public Publisher<EmailAddressContact> listDomainsContacts() {
        return Mono.empty();
    }

    @Override
    public Publisher<EmailAddressContact> get(AccountId accountId, MailAddress mailAddress) {
        return Mono.empty();
    }

    @Override
    public Publisher<EmailAddressContact> get(Domain domain, MailAddress mailAddress) {
        return Mono.empty();
    }
}
