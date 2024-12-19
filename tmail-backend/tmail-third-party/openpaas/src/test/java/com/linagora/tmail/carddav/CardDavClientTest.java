package com.linagora.tmail.carddav;

import static com.linagora.tmail.carddav.CardDavServerExtension.CARD_DAV_ADMIN_WITH_DELEGATED_AUTHORIZATION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockserver.model.NottableString.string;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class CardDavClientTest {
    private static final String OPENPAAS_USER_NAME = "openpaasUserName1";
    private static final String OPENPAAS_USER_ID = "openpaasUserId1";

    @RegisterExtension
    static CardDavServerExtension cardDavServerExtension = new CardDavServerExtension();

    private CardDavClient client;

    @BeforeEach
    void setup() {
        client = new CardDavClient.OpenpaasCardDavClient(cardDavServerExtension.getCardDavConfiguration());
    }

    @Test
    void existsCollectedContactShouldReturnTrueWhenHTTPResponseIs200() {
        String collectedContactUid = UUID.randomUUID().toString();
        cardDavServerExtension.setCollectedContactExists(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid, true);
        assertThat(client.existsCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid).block())
            .isTrue();
    }

    @Test
    void existsCollectedContactShouldReturnFalseWhenHTTPResponseIs404() {
        String collectedContactUid = UUID.randomUUID().toString();
        cardDavServerExtension.setCollectedContactExists(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid, false);
        assertThat(client.existsCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid).block())
            .isFalse();
    }

    @Test
    void existsCollectedContactShouldReturnFalseWhenHTTPResponseIs500(ClientAndServer mockServer) {
        String collectedContactUid = UUID.randomUUID().toString();
        mockServer.when(HttpRequest.request()
                .withMethod("GET")
                .withPath("/addressbooks/" + OPENPAAS_USER_ID + "/collected/" + collectedContactUid + ".vcf")
                .withHeader(string("Authorization"), string(CARD_DAV_ADMIN_WITH_DELEGATED_AUTHORIZATION.apply(OPENPAAS_USER_NAME))))
            .respond(HttpResponse.response()
                .withStatusCode(500));

        assertThatThrownBy(() -> client.existsCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid).block())
            .isInstanceOf(CardDavClientException.class);
    }

    @Test
    void createCollectedContactShouldNotThrowWhenHTTPResponseIs201() throws Exception {
        String collectedContactUid = UUID.randomUUID().toString();
        cardDavServerExtension.setCreateCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid);

        CardDavCreationObjectRequest request = new CardDavCreationObjectRequest(
            "4.0",
            collectedContactUid,
            Optional.of("An Bach4"),
            Optional.of(List.of("An", "Bach4")),
            new CardDavCreationObjectRequest.Email(
                List.of(CardDavCreationObjectRequest.EmailType.HOME),
                new MailAddress("anbach4@lina.com")));

        assertThatCode(() -> client.createCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, request).block())
            .doesNotThrowAnyException();
    }

    @Test
    void createCollectedContactShouldThrowWhenHTTPResponseIs404(ClientAndServer mockServer) throws Exception {
        String collectedContactUid = UUID.randomUUID().toString();
        mockServer.when(HttpRequest.request()
                .withMethod("PUT")
                .withPath("/addressbooks/user1/collected/" + collectedContactUid + ".vcf")
                .withHeader(string("Authorization"), string(CARD_DAV_ADMIN_WITH_DELEGATED_AUTHORIZATION.apply(OPENPAAS_USER_NAME))))
            .respond(HttpResponse.response()
                .withStatusCode(404));

        CardDavCreationObjectRequest request = new CardDavCreationObjectRequest(
            "4.0",
            collectedContactUid,
            Optional.of("An Bach4"),
            Optional.of(List.of("An", "Bach4")),
            new CardDavCreationObjectRequest.Email(
                List.of(CardDavCreationObjectRequest.EmailType.HOME),
                new MailAddress("anbach4@lina.com")));

        assertThatThrownBy(() -> client.createCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, request).block())
            .isInstanceOf(CardDavClientException.class);
    }
}
