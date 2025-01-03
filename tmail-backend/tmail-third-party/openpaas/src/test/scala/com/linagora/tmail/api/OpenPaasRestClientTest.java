package com.linagora.tmail.api;

import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;
import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

public class OpenPaasRestClientTest {
    public static final String BAD_USER_ID = "BAD_ID";
    @RegisterExtension
    static OpenPaasServerExtension openPaasServerExtension = new OpenPaasServerExtension();

    OpenPaasRestClient restClient;

    @BeforeEach
    void setup() throws URISyntaxException {
        OpenPaasConfiguration openPaasConfig = new OpenPaasConfiguration(
            openPaasServerExtension.getBaseUrl(),
            OpenPaasServerExtension.GOOD_USER(),
            OpenPaasServerExtension.GOOD_PASSWORD(),
            OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED,
           new OpenPaasConfiguration.ContactConsumerConfiguration(
               ImmutableList.of(AmqpUri.from("amqp://not_important.com")),
                OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED));

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
    void shouldThrowExceptionOnErrorStatusCode() throws URISyntaxException {
        OpenPaasConfiguration openPaasConfig = new OpenPaasConfiguration(
            openPaasServerExtension.getBaseUrl(),
            OpenPaasServerExtension.BAD_USER(),
            OpenPaasServerExtension.BAD_PASSWORD(),
            OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED,
            new OpenPaasConfiguration.ContactConsumerConfiguration(
                AmqpUri.from("amqp://not_important.com"),
                OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED));

        restClient = new OpenPaasRestClient(openPaasConfig);

        assertThatThrownBy(() -> restClient.retrieveMailAddress(OpenPaasServerExtension.ALICE_USER_ID()).block())
            .isInstanceOf(OpenPaasRestClientException.class);
    }

    @Test
    void searchOpenPaasUserIdShouldReturnUserIdWhenUserExists() {
        assertThat(restClient.searchOpenPaasUserId(OpenPaasServerExtension.ALICE_EMAIL()).block())
            .isEqualTo(OpenPaasServerExtension.ALICE_USER_ID());
    }

    @Test
    void searchOpenPaasUserIdShouldReturnEmptyWhenSearchResultIsEmpty() {
        assertThat(restClient.searchOpenPaasUserId(OpenPaasServerExtension.NOTFOUND_EMAIL()).blockOptional())
            .isEmpty();
    }

    @Test
    void searchOpenPaasUserIdShouldReturnEmptyWhenResponseError() {
        assertThat(restClient.searchOpenPaasUserId(OpenPaasServerExtension.ERROR_EMAIL()).blockOptional())
            .isEmpty();
    }

    // This method is used to get the rest client manually for integration test
    private OpenPaasRestClient getRestClientManuallyForTest() throws URISyntaxException {
        OpenPaasConfiguration openPaasConfig = new OpenPaasConfiguration(
            new URI("http://localhost:8080/api"),
            "admin@open-paas.org",
            "secret",
            OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED,
            Optional.empty(), Optional.empty());

        return new OpenPaasRestClient(openPaasConfig);
    }
}
