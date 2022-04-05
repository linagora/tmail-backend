package com.linagora.tmail.james.common.probe;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.utils.GuiceProbe;

import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;

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
}
