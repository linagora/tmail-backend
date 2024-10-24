package com.linagora.tmail.contact;

import java.util.Optional;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JCardObjectDeserializerTest {
    static ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldNotThrowWhenMailAddressIsInvalid() {
        JCardObject cardObject = deserialize("""
            [
             "vcard",
               [
                 [ "fn",      {}, "text", "Jhon Doe" ],
                 [ "email",   {}, "text", "BAD_MAIL_ADDRESS" ]
               ]
            ]
            """);

        assertThat(cardObject.emailOpt()).isEmpty();
        assertThat(cardObject.fnOpt()).isEqualTo(Optional.of("Jhon Doe"));
    }

    @Test
    void shouldNotThrowWhenJCardContainsUnknownProperties() {
        JCardObject cardObject = deserialize("""
            [
             "vcard",
               [
                  [ "version", {}, "text", "4.0" ],
                  [ "kind",    {}, "text", "individual" ],
                  [ "fn",      {}, "text", "Jhon Doe" ],
                  [ "email",   {}, "text", "jhon@doe.com" ],
                  [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
               ]
            ]""");

        assertThat(cardObject.emailOpt()).isEqualTo(Optional.of("jhon@doe.com"));
        assertThat(cardObject.fnOpt()).isEqualTo(Optional.of("Jhon Doe"));
    }

    @Test
    void shouldNotThrowWhenEmailPropertyNotPresent() {
        JCardObject cardObject = deserialize("""
            [
             "vcard",
               [
                 [ "fn",      {}, "text", "Jhon Doe" ]
               ]
            ]
            """);

        assertThat(cardObject.emailOpt()).isEmpty();
        assertThat(cardObject.fnOpt()).isEqualTo(Optional.of("Jhon Doe"));
    }

    @Test
    void shouldNotThrowWhenFnPropertyNotPresent() {
        JCardObject cardObject = deserialize("""
            [
             "vcard",
               [
                 [ "email",   {}, "text", "jhon@doe.com" ]
               ]
            ]
            """);

        assertThat(cardObject.emailOpt()).isEqualTo(Optional.of("jhon@doe.com"));
        assertThat(cardObject.fnOpt()).isEmpty();
    }

    @Test
    void shouldThrowOnBadPayload() {
        assertThatThrownBy(() -> deserialize("BAD_PAYLOAD"));
    }

    private JCardObject deserialize(String json) {
        try {
            return objectMapper.readValue(json, JCardObject.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
