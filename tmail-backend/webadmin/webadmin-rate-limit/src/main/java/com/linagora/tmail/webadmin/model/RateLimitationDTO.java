package com.linagora.tmail.webadmin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RateLimitationDTO(String name,
                                Long periodInSeconds,
                                Long count,
                                Long size) {
    @JsonCreator
    public RateLimitationDTO(@JsonProperty(value = "name", required = true) String name,
                             @JsonProperty(value = "periodInSeconds", required = true) Long periodInSeconds,
                             @JsonProperty(value = "count", required = true) Long count,
                             @JsonProperty(value = "size", required = true) Long size) {
        this.name = name;
        this.periodInSeconds = periodInSeconds;
        this.count = count;
        this.size = size;
    }
}
