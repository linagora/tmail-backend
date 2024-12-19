package com.linagora.tmail.carddav;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

public class CardDavCreationObjectRequestTest {

    @Test
    void testToVCardWithAllFields() throws Exception {
        CardDavCreationObjectRequest.Email email = new CardDavCreationObjectRequest.Email(
            List.of(CardDavCreationObjectRequest.EmailType.HOME),
            new MailAddress("anbach4@lina.com"));

        CardDavCreationObjectRequest request = new CardDavCreationObjectRequest(
            "4.0",
            "123",
            Optional.of("An Bach4"),
            Optional.of(List.of("An", "Bach4")),
            email);

       assertThat(request.toVCard())
           .isEqualTo("""
                 BEGIN:VCARD
                 VERSION:4.0
                 FN:An Bach4
                 N:An;Bach4;;;
                 UID:123
                 EMAIL:anbach4@lina.com
                 END:VCARD""");
    }

    @Test
    void testToVCardWithoutOptionalFields() throws Exception {
        CardDavCreationObjectRequest.Email email = new CardDavCreationObjectRequest.Email(
            List.of(CardDavCreationObjectRequest.EmailType.WORK),
            new MailAddress("work@example.com"));

        CardDavCreationObjectRequest request = new CardDavCreationObjectRequest(
            "4.0",
            "uid123",
            Optional.empty(),
            Optional.empty(),
            email);

        assertThat(request.toVCard())
            .isEqualTo("""
                 BEGIN:VCARD
                 VERSION:4.0
                 UID:uid123
                 EMAIL:work@example.com
                 END:VCARD""");
    }
}
