package com.linagora.tmail.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class JCardObjectDeserializerTest {
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

        assertThat(cardObject.mailAddresses()).isEmpty();
        assertThat(cardObject.fnOpt()).isEqualTo(Optional.of("Jhon Doe"));
    }

    @Test
    void shouldNotThrowWhenJCardContainsUnknownProperties() throws AddressException {
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

        assertThat(cardObject.mailAddresses().getFirst()).isEqualTo(new MailAddress("jhon@doe.com"));
        assertThat(cardObject.fnOpt()).isEqualTo(Optional.of("Jhon Doe"));
    }

    @Test
    void shouldSupportMultipleEmailAddresses() throws AddressException {
        JCardObject cardObject = deserialize("""
            [
                     "vcard",
                     [
                         [
                             "version",
                             {},
                             "text",
                             "4.0"
                         ],
                         [
                             "uid",
                             {},
                             "text",
                             "9e342040-440f-4f7f-a603-3d4f8f7c3952"
                         ],
                         [
                             "fn",
                             {},
                             "text",
                             "Jhon Doe"
                         ],
                         [
                             "n",
                             {},
                             "text",
                             [
                                 "",
                                 "Jhon Doe",
                                 "",
                                 "",
                                 ""
                             ]
                         ],
                         [
                             "email",
                             {
                                 "type": "Work"
                             },
                             "text",
                             "jhondoe@yahoo.co.in"
                         ],
                         [
                             "email",
                             {
                                 "type": "Home"
                             },
                             "text",
                             "jhondoe@gmail.com"
                         ],
                         [
                             "tel",
                             {
                                 "type": "Work"
                             },
                             "uri",
                             "57847703"
                         ],
                         [
                             "tel",
                             {
                                 "type": "Mobile"
                             },
                             "uri",
                             "2839192"
                         ]
                     ],
                     []
                 ]""");

        assertThat(cardObject.mailAddresses())
            .containsExactlyInAnyOrder(
                new MailAddress("jhondoe@yahoo.co.in"),
                new MailAddress("jhondoe@gmail.com"));
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

        assertThat(cardObject.mailAddresses()).isEmpty();
        assertThat(cardObject.fnOpt()).isEqualTo(Optional.of("Jhon Doe"));
    }

    @Test
    void shouldNotThrowWhenFnPropertyNotPresent() throws AddressException {
        JCardObject cardObject = deserialize("""
            [
             "vcard",
               [
                 [ "email",   {}, "text", "jhon@doe.com" ]
               ]
            ]
            """);

        assertThat(cardObject.mailAddresses().getFirst()).isEqualTo(new MailAddress("jhon@doe.com"));
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
