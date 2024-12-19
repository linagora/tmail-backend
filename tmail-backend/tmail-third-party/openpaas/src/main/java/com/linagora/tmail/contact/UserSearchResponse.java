package com.linagora.tmail.contact;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public record UserSearchResponse(@JsonProperty("_id") String id,
                                 @JsonProperty("preferredEmail") String preferredEmail,
                                 @JsonProperty("emails") List<String> emails) {

    public static class Deserializer {
        private final ObjectMapper objectMapper;

        public Deserializer() {
            this.objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        public List<UserSearchResponse> deserialize(byte[] jsonBytes) throws IOException {
            return objectMapper.readValue(jsonBytes, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, UserSearchResponse.class));
        }
    }
}