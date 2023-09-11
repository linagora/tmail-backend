package com.linagora.tmail.integration;

import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.util.Map;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.common.probe.JmapGuiceContactAutocompleteProbe;
import com.linagora.tmail.james.common.probe.JmapGuiceKeystoreManagerProbe;
import com.linagora.tmail.james.common.probe.JmapGuiceLabelProbe;
import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.LabelCreationRequest;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import scala.Option;

public abstract class UserDeletionIntegrationContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    private static final String DOMAIN = "linagora.com";
    private static final Username ALICE  = Username.fromLocalPartWithDomain("alice", DOMAIN);
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final AccountId ALICE_ACCOUNT_ID = AccountId.fromUsername(ALICE);
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
    void shouldDeleteContacts(GuiceJamesServer server) throws Exception {
        ContactFields contactFields = new ContactFields(new MailAddress("andre@linagora.com"), "Andre", "Dupont");
        server.getProbe(JmapGuiceContactAutocompleteProbe.class)
            .index(ALICE_ACCOUNT_ID, contactFields);

        awaitDocumentsIndexed(1L);

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .body("additionalInformation.status.ContactUserDataDeletionTaskStep", Matchers.is("DONE"));

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(server.getProbe(JmapGuiceContactAutocompleteProbe.class).list(ALICE_ACCOUNT_ID))
                .isEmpty());
    }

    @Test
    void shouldDeletePGPPublicKeys(GuiceJamesServer server) throws Exception {
        byte[] publicKeyPayload = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        JmapGuiceKeystoreManagerProbe keystoreManagerProbe = server.getProbe(JmapGuiceKeystoreManagerProbe.class);
        keystoreManagerProbe.save(ALICE, publicKeyPayload);

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .body("additionalInformation.status.PGPKeysUserDeletionTaskStep", Matchers.is("DONE"));

        assertThat(keystoreManagerProbe.getKeyPayLoads(ALICE))
            .isEmpty();
    }

    @Test
    void shouldDeleteLabels(GuiceJamesServer server) {
        JmapGuiceLabelProbe labelProbe = server.getProbe(JmapGuiceLabelProbe.class);
        labelProbe.addLabel(ALICE, new LabelCreationRequest(new DisplayName("Important"), Option.empty()));

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .body("additionalInformation.status.LabelUserDeletionTaskStep", Matchers.is("DONE"));

        assertThat(labelProbe.listLabels(ALICE))
            .isEmpty();
    }

    @Test
    void shouldDeleteJmapSettings(GuiceJamesServer server) {
        JmapSettingsProbe settingsProbe = server.getProbe(JmapSettingsProbe.class);

        settingsProbe.reset(ALICE, Map.of("key", "value"));

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .body("additionalInformation.status.JmapSettingsUserDeletionTaskStep", Matchers.is("DONE"));

        assertThat(settingsProbe.get(BOB)).isNull();
    }
}
