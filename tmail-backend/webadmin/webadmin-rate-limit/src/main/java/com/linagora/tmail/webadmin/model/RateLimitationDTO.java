package com.linagora.tmail.webadmin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RateLimitationDTO {
    private final String name;
    private final Long periodInSeconds;
    private final Long count;
    private final Long size;

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

    public String getName() {
        return name;
    }

    public Long getPeriodInSeconds() {
        return periodInSeconds;
    }

    public Long getCount() {
        return count;
    }

    public Long getSize() {
        return size;
    }
}
