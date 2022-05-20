package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmailAddressContactDTO(@JsonProperty("emailAddress") String emailAddress,
                                     @JsonProperty("firstname") Optional<String> firstname,
                                     @JsonProperty("surname") Optional<String> surname) {
}
