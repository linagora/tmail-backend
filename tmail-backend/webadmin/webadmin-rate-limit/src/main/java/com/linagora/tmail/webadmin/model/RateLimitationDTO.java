package com.linagora.tmail.webadmin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RateLimitationDTO {
    private final String name;
    private final Long period;
    private final Long count;
    private final Long size;

    @JsonCreator
    public RateLimitationDTO(@JsonProperty(value = "name", required = true) String name,
                             @JsonProperty(value = "period", required = true) Long period,
                             @JsonProperty(value = "count", required = true) Long count,
                             @JsonProperty(value = "size", required = true) Long size) {
        this.name = name;
        this.period = period;
        this.count = count;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public Long getPeriod() {
        return period;
    }

    public Long getCount() {
        return count;
    }

    public Long getSize() {
        return size;
    }
}
