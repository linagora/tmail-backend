package com.linagora.tmail.contact;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public record ContactRabbitMqMessage(JCardObject vcard,
                                     @JsonProperty(value = "_id") String openPaasUserId,
                                     @JsonProperty(value = "userId") String userId,
                                     @JsonProperty(value = "user") User user) {

    public record User(@JsonProperty(value = "_id") String _id) {}

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
        return Optional.ofNullable(this.openPaasUserId)
            .or(() -> Optional.ofNullable(this.userId))
            .or(() -> Optional.ofNullable(this.user)
                .map(User::_id))
            .orElse(null);
    }
}
