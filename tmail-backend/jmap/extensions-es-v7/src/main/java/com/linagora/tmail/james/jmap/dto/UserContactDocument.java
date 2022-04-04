package com.linagora.tmail.james.jmap.dto;

import java.util.UUID;

import org.apache.james.jmap.api.model.AccountId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;

public class UserContactDocument {
    private final String accountId;
    private final UUID contactId;
    private final String email;
    private final String firstname;
    private final String surname;

    public UserContactDocument(AccountId accountId, EmailAddressContact contact) {
        this.accountId = accountId.getIdentifier();
        this.contactId = contact.id();
        this.email = contact.fields().address().asString();
        this.firstname = contact.fields().firstname();
        this.surname = contact.fields().surname();
    }

    @JsonProperty("accountId")
    public String getAccountId() {
        return accountId;
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
