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
            nameOrEmpty(emailAddressContact.fields().surname())
        );
    }

    private static Optional<String> nameOrEmpty(String name) {
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

}
