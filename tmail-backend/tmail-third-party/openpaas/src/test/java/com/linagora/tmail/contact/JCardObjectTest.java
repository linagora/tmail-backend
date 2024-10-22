package com.linagora.tmail.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Optional;

public class JCardObjectTest {

    @Test
    void asContactFieldsShouldNotThrowIfFullnameNotProvided() {
        JCardObject cardObject = new JCardObject(Optional.empty(), Optional.of("Jhon@doe.com"));
        assertThat(cardObject.asContactFields()).isPresent();
    }

    @Test
    void asContactFieldsShouldConvertFieldsCorrectly() {
        JCardObject cardObject = new JCardObject(Optional.of("Jhon Doe"), Optional.of("Jhon@doe.com"));
        assertThat(cardObject.asContactFields()).isPresent();
        assertThat(cardObject.asContactFields().get().firstname()).isEqualTo("Jhon Doe");
        assertThat(cardObject.asContactFields().get().surname()).isEmpty();
        assertThat(cardObject.asContactFields().get().address()).isEqualTo("Jhon@doe.com");
    }

    @Test
    void asContactFieldsShouldNotThrowIfMailAddressIsInvalid() {
        JCardObject cardObject = new JCardObject(Optional.of("BAD_EMAIL_ADDRESS"), Optional.of("Jhon Doe"));
        assertThat(cardObject.asContactFields()).isEmpty();
    }

    @Test
    void shouldThrowIfFnOrEmailOptionalIsNull() {
        assertThatThrownBy(
            () -> new JCardObject(null, Optional.of("jhon@doe.com"))
        ).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(
            () -> new JCardObject(Optional.of("Jhon Doe"), null)
        ).isInstanceOf(NullPointerException.class);
    }
}
