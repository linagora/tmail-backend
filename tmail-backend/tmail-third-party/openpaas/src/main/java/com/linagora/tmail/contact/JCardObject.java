package com.linagora.tmail.contact;

import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.linagora.tmail.james.jmap.contact.ContactFields;

@JsonDeserialize(using = JCardObjectDeserializer.class)
public record JCardObject(Optional<String> fnOpt, Optional<MailAddress> emailOpt) {
    public static final Logger LOGGER = LoggerFactory.getLogger(JCardObject.class);

    public JCardObject {
        Preconditions.checkNotNull(fnOpt);
        Preconditions.checkNotNull(emailOpt);
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
     * Purpose: To specify the electronic mail address for communication
     * with the object the vCard represents.
     * <p>
     * Example: jane_doe@example.com
     */
    @Override
    public Optional<MailAddress> emailOpt() {
        return emailOpt;
    }

    public Optional<ContactFields> asContactFields() {
        Optional<String> contactFullnameOpt = fnOpt();
        Optional<MailAddress> contactMailAddressOpt = emailOpt();

        if (contactMailAddressOpt.isEmpty()) {
            return Optional.empty();
        }

        MailAddress contactMailAddress = contactMailAddressOpt.get();
        String contactFullname = contactFullnameOpt.orElse("");

        return Optional.of(new ContactFields(contactMailAddress, contactFullname, ""));
    }
}
