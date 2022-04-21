package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.linagora.tmail.james.jmap.contact.EmailAddressContact;

public class EmailAddressContactResponse {
    private final String id;
    private final String emailAddress;
    private final Optional<String> firstname;
    private final Optional<String> surname;

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

    public EmailAddressContactResponse(String id, String emailAddress, Optional<String> firstname, Optional<String> surname) {
        this.id = id;
        this.emailAddress = emailAddress;
        this.firstname = firstname;
        this.surname = surname;
    }

    public String getId() {
        return id;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public Optional<String> getFirstname() {
        return firstname;
    }

    public Optional<String> getSurname() {
        return surname;
    }
}
