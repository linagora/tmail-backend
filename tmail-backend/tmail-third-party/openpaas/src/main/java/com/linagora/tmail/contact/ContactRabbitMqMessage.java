package com.linagora.tmail.contact;

import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public record ContactRabbitMqMessage(String bookId, String bookName, String contactId,
                                          String userId, JCardObject vcard) {

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
}
