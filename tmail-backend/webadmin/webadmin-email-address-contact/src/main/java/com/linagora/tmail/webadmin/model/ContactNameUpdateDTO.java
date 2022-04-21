package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ContactNameUpdateDTO {
    private final Optional<String> firstname;
    private final Optional<String> surname;

    public ContactNameUpdateDTO(@JsonProperty("firstname") Optional<String> firstname,
                                @JsonProperty("surname") Optional<String> surname) {
        this.firstname = firstname;
        this.surname = surname;
    }

    public Optional<String> getFirstname() {
        return firstname;
    }

    public Optional<String> getSurname() {
        return surname;
    }
}
