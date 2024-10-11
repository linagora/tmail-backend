package com.linagora.tmail.api;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.OpenPaasConfiguration;

public class OpenPaasRestClientTest {
    public static final String BAD_USER_ID = "BAD_ID";
    @RegisterExtension
    static OpenPaasServerExtension openPaasServerExtension = new OpenPaasServerExtension();

    OpenPaasRestClient restClient;

    @BeforeEach
    void setup() {
        OpenPaasConfiguration openPaasConfig = new OpenPaasConfiguration(
            openPaasServerExtension.getBaseUrl(),
            OpenPaasServerExtension.GOOD_USER(),
            OpenPaasServerExtension.GOOD_PASSWORD()
        );

        restClient = new OpenPaasRestClient(openPaasConfig);
    }

    @Test
    void shouldReturnUserMailAddressWhenUserIdAndAuthenticationTokenIsCorrect()
        throws AddressException {
        assertThat(restClient.retrieveMailAddress(OpenPaasServerExtension.ALICE_USER_ID()).block())
            .isEqualTo(new MailAddress(OpenPaasServerExtension.ALICE_EMAIL()));
    }

    @Test
    void shouldReturnEmptyMonoWhenUserWithIdNotFound() {
        assertThat(restClient.retrieveMailAddress(BAD_USER_ID).blockOptional()).isEmpty();
    }

    @Test
    void shouldThrowExceptionOnErrorStatusCode() {
        OpenPaasConfiguration openPaasConfig = new OpenPaasConfiguration(
            openPaasServerExtension.getBaseUrl(),
            OpenPaasServerExtension.BAD_USER(),
            OpenPaasServerExtension.BAD_PASSWORD()
        );

        restClient = new OpenPaasRestClient(openPaasConfig);

        assertThatThrownBy(() -> restClient.retrieveMailAddress(OpenPaasServerExtension.ALICE_USER_ID()).block())
            .isInstanceOf(OpenPaasRestClientException.class);
    }
}
