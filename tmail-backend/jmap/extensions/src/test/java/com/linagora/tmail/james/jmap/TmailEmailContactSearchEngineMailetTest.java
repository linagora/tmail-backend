package com.linagora.tmail.james.jmap;

import com.linagora.tmail.james.jmap.model.EmailAddressContact;
import com.linagora.tmail.james.jmap.model.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.model.InMemoryEmailAddressContactSearchEngine;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import javax.mail.MessagingException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TmailEmailContactSearchEngineMailetTest {
    final EmailAddressContactSearchEngine emailAddressContactSearchEngine
            = new InMemoryEmailAddressContactSearchEngine();

    public TmailEmailContactSearchEngineMailet testee() {
        return new TmailEmailContactSearchEngineMailet(emailAddressContactSearchEngine);
    }

    @Test
    void indexShouldWorkWhenMailSent() throws MessagingException {
        Mail mail = FakeMail.builder().name("fake mail").sender("shizuka@linagora.com")
                .recipients("nobita@gmail.com", "doraemon@linagora.com").build();
        testee().service(mail);
        EmailAddressContact contact1 = new EmailAddressContact(UUID.randomUUID(), "nobita@linagora.com");
        assertThat(Flux.from(emailAddressContactSearchEngine.autoComplete(AccountId.fromString("shizuka@linagora.com"),
                "nob")).collectList().block().contains(contact1));

    }
}
