package com.linagora.tmail.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenPaasUserResponse(@JsonProperty("id") String id,
                                   @JsonProperty("firstname") String firstname,
                                   @JsonProperty("lastname") String lastname,
                                   @JsonProperty("preferredEmail") String preferredEmail,
                                   @JsonProperty("emails") List<String> emails,
                                   @JsonProperty("main_phone") String mainPhone,
                                   @JsonProperty("displayName") String displayName) {
}
