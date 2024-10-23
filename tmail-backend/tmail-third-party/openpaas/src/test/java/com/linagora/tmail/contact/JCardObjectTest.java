package com.linagora.tmail.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import jakarta.mail.internet.AddressException;

public class JCardObjectTest {

    @Test
    void asContactFieldsShouldNotThrowIfFullnameNotProvided() {
        JCardObject cardObject = new JCardObject(Optional.empty(), maybeMailAddress("Jhon@doe.com"));
        assertThat(cardObject.asContactFields()).isPresent();
    }

    @Test
    void asContactFieldsShouldConvertFieldsCorrectly() {
        JCardObject cardObject = new JCardObject(Optional.of("Jhon Doe"), maybeMailAddress("Jhon@doe.com"));
        assertThat(cardObject.asContactFields()).isPresent();
        assertThat(cardObject.asContactFields().get().firstname()).isEqualTo("Jhon Doe");
        assertThat(cardObject.asContactFields().get().surname()).isEmpty();
        assertThat(cardObject.asContactFields().get().address()).isEqualTo("Jhon@doe.com");
    }

    @Test
    void shouldThrowIfFnOrEmailOptionalIsNull() {
        assertThatThrownBy(() -> new JCardObject(null, maybeMailAddress("jhon@doe.com")))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new JCardObject(Optional.of("Jhon Doe"), null))
            .isInstanceOf(NullPointerException.class);
    }

    private Optional<MailAddress> maybeMailAddress(String email) {
        try {
            return Optional.of(new MailAddress(email));
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }
}
