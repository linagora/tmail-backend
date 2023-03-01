package com.linagora.tmail.integration;

import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.common.probe.JmapGuiceContactAutocompleteProbe;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

public abstract class UsernameChangeIntegrationContract {
    private static final String DOMAIN = "linagora.com";
    private static final Username ALICE  = Username.fromLocalPartWithDomain("alice", DOMAIN);
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final AccountId ALICE_ACCOUNT_ID = AccountId.fromUsername(ALICE);
    private static final AccountId BOB_ACCOUNT_ID = AccountId.fromUsername(BOB);
    private static final String PASSWORD = "123456";

    private RequestSpecification webAdminApi;

    public abstract void awaitDocumentsIndexed(Long documentCount);

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(ALICE.asString(), PASSWORD)
            .addUser(BOB.asString(), PASSWORD);

        Port jmapPort = server.getProbe(JmapGuiceProbe.class).getJmapPort();
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapPort.getValue())
            .build();

        webAdminApi = WebAdminUtils.spec(server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @Test
    void shouldAdaptContacts(GuiceJamesServer server) throws Exception {
        ContactFields contactFields = new ContactFields(new MailAddress("andre@linagora.com"), "Andre", "Dupont");

        JmapGuiceContactAutocompleteProbe contactProbe = server.getProbe(JmapGuiceContactAutocompleteProbe.class);
        EmailAddressContact contact = contactProbe.index(ALICE_ACCOUNT_ID, contactFields);

        String taskId = webAdminApi
            .queryParam("action", "rename")
            .post("/users/" + ALICE.asString() + "/rename/" + BOB.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        assertThat(contactProbe.list(BOB_ACCOUNT_ID))
            .containsOnly(contact);
        assertThat(contactProbe.list(ALICE_ACCOUNT_ID))
            .isEmpty();
    }
}
