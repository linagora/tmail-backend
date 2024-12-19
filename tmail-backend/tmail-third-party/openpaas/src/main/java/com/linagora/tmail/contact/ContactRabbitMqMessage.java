package com.linagora.tmail.contact;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public record ContactRabbitMqMessage(JCardObject vcard,
                                     @JsonProperty(value = "_id") String openPaasUserId,
                                     @JsonProperty(value = "userId") String userId) {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    static ContactRabbitMqMessage fromJSON(byte[] jsonBytes) {
        try {
            return objectMapper.readValue(jsonBytes, ContactRabbitMqMessage.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse ContactRabbitMqMessage", e);
        }
    }

    public String openPaasUserId() {
        return StringUtils.defaultIfEmpty(this.openPaasUserId,this.userId);
    }
}
