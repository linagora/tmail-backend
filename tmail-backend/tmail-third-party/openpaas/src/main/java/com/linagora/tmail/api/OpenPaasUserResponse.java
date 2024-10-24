package com.linagora.tmail.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenPaasUserResponse(@JsonProperty("preferredEmail") String preferredEmail) {
}
