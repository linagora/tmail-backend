package com.linagora.tmail.james.jmap;

import com.linagora.tmail.james.jmap.model.EmailAddressContact;
import com.linagora.tmail.james.jmap.model.EmailAddressContactSearchEngine;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import reactor.core.publisher.Flux;

import javax.mail.MessagingException;
import java.util.UUID;

public class TmailEmailContactSearchEngineMailet extends GenericMailet {
    final EmailAddressContactSearchEngine emailAddressContactSearchEngine;

    public TmailEmailContactSearchEngineMailet(EmailAddressContactSearchEngine emailAddressContactSearchEngine) {
        this.emailAddressContactSearchEngine = emailAddressContactSearchEngine;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        Flux.fromIterable(mail.getRecipients())
            .map(recipient -> emailAddressContactSearchEngine
                 .index(AccountId.fromString(mail.getMaybeSender().asString()),
                        new EmailAddressContact(UUID.fromString(recipient.getLocalPart()),
                                                recipient.getLocalPart()+"@"+recipient.getDomain())));
    }
}
