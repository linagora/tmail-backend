package com.linagora.tmail.james.jmap.contact;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenPaasUserResponse(@JsonProperty("id") String id,
                                   @JsonProperty("firstname") String firstname,
                                   @JsonProperty("lastname") String lastname,
                                   @JsonProperty("preferredEmail") String preferredEmail,
                                   @JsonProperty("emails") List<String> emails,
                                   @JsonProperty("main_phone") String mainPhone,
                                   @JsonProperty("displayName") String displayName) {

    @Override
    public String firstname() {
        return firstname;
    }

    @Override
    public String lastname() {
        return lastname;
    }

    @Override
    public String preferredEmail() {
        return preferredEmail;
    }

    @Override
    public List<String> emails() {
        return emails;
    }

    @Override
    public String mainPhone() {
        return mainPhone;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }
}
