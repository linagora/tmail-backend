package com.linagora.tmail.carddav;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

import net.javacrumbs.jsonunit.core.Option;

public class CardDavCreationSerializerTest {

    CardDavCreationSerializer testee = new CardDavCreationSerializer();

    @Test
    void testSerializeWithAllFields() throws Exception {
        // Arrange
        CardDavCreationObjectRequest.Email email = new CardDavCreationObjectRequest.Email(
            List.of(CardDavCreationObjectRequest.EmailType.HOME),
            new MailAddress("anbach4@lina.com")
        );

        CardDavCreationObjectRequest request = new CardDavCreationObjectRequest(
            "4.0",
            "123",
            Optional.of("An Bach4"),
            Optional.of(List.of("An", "Bach4")),
            email
        );

        // Act
        String jsonResult = testee.serializeAsString(request);

        // Assert
        String expectedJson = """
            [
               "vcard",
               [
                 [ "version", {}, "text", "4.0" ],
                 [ "uid", {}, "text", "123" ],
                 [ "fn", {}, "text", "An Bach4" ],
                 [ "n", {}, "text", [ "An", "Bach4" ] ],
                 [ "email", { type: [ "Home" ] }, "text", "mailto:anbach4@lina.com" ]
               ],
               []
             ]
            """;

        assertThatJson(jsonResult)
            .withOptions(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(expectedJson);
    }

    @Test
    void testSerializeWithoutOptionalFields() throws Exception {
        CardDavCreationObjectRequest.Email email = new CardDavCreationObjectRequest.Email(
            List.of(CardDavCreationObjectRequest.EmailType.WORK),
            new MailAddress("work@example.com")
        );

        CardDavCreationObjectRequest request = new CardDavCreationObjectRequest(
            "4.0",
            "uid123",
            Optional.empty(),
            Optional.empty(),
            email
        );

        // Act
        String jsonResult = testee.serializeAsString(request);

        // Assert
        String expectedJson = """
            [
               "vcard",
               [
                 [ "version", {}, "text", "4.0" ],
                 [ "uid", {}, "text", "uid123" ],
                 [ "email", { type: [ "Work" ] }, "text", "mailto:work@example.com" ]
               ],
               []
             ]
            """;

        assertThatJson(jsonResult).isEqualTo(expectedJson);
    }
}
