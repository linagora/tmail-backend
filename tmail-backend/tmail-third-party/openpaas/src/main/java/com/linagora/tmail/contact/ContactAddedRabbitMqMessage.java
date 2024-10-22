package com.linagora.tmail.contact;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public record ContactAddedRabbitMqMessage(String bookId, String bookName, String contactId,
                                          String userId, JCardObject vcard) {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static ContactAddedRabbitMqMessage fromJSON(byte[] jsonBytes) {
        String message = new String(jsonBytes, StandardCharsets.UTF_8);
        try {
            return objectMapper.readValue(message, ContactAddedRabbitMqMessage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse ContactAddedRabbitMqMessage", e);
        }
    }
}
