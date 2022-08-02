package com.linagora.tmail.james.jmap.dto;

import java.util.UUID;

import org.apache.james.core.Domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;

public class DomainContactDocument {
    private final String domain;
    private final UUID contactId;
    private final String email;
    private final String firstname;
    private final String surname;

    public DomainContactDocument(Domain domain, EmailAddressContact contact) {
        this.domain = domain.asString();
        this.contactId = contact.id();
        this.email = contact.fields().address().asString();
        this.firstname = contact.fields().firstname();
        this.surname = contact.fields().surname();
    }

    @JsonProperty("domain")
    public String getDomain() {
        return domain;
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
