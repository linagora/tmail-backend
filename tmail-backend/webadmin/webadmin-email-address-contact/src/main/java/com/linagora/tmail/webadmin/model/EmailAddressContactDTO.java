package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EmailAddressContactDTO {
    private final String emailAddress;
    private final Optional<String> firstname;
    private final Optional<String> surname;

    public EmailAddressContactDTO(@JsonProperty("emailAddress") String emailAddress,
                                  @JsonProperty("firstname") Optional<String> firstname,
                                  @JsonProperty("surname") Optional<String> surname) {
        this.emailAddress = emailAddress;
        this.firstname = firstname;
        this.surname = surname;
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
