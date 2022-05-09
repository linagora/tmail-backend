package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ContactNameUpdateDTO(Optional<String> firstname, Optional<String> surname) {
    public ContactNameUpdateDTO(@JsonProperty("firstname") Optional<String> firstname,
                                @JsonProperty("surname") Optional<String> surname) {
        this.firstname = firstname;
        this.surname = surname;
    }
}
