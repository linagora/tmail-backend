package com.linagora.tmail.api;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

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
            OpenPaasServerExtension.REST_CLIENT_USER(),
            OpenPaasServerExtension.REST_CLIENT_PASSWORD()
        );

        restClient = new OpenPaasRestClient(openPaasConfig);
    }

    @Test
    void shouldReturnUserResponseWhenUserIdAndAuthenticationTokenIsCorrect() {
        assertThat(restClient.getUserById(OpenPaasServerExtension.ALICE_USER_ID()).block()).isPresent();
    }

    @Test
    void shouldReturnOptionalEmptyUserWithIdNotFound() {
        assertThat(restClient.getUserById(BAD_USER_ID).block()).isEmpty();
    }

    @Test
    void shouldReturnStatusCode401WhenAuthenticationTokenWasInvalid() {

    }
}
