/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.integration;

import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.util.Map;
import java.util.UUID;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.jmap.api.model.AccountId;
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

import com.linagora.tmail.james.common.PublicAssetGetMethodContract;
import com.linagora.tmail.james.common.probe.JmapGuiceContactAutocompleteProbe;
import com.linagora.tmail.james.common.probe.JmapGuiceKeystoreManagerProbe;
import com.linagora.tmail.james.common.probe.JmapGuiceLabelProbe;
import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.james.common.probe.PublicAssetProbe;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.LabelCreationRequest;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import scala.Option;
import scala.jdk.javaapi.CollectionConverters;

public abstract class UserDeletionIntegrationContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    private static final String DOMAIN = "linagora.com";
    private static final String PASSWORD = "123456";

    private Username alice;
    private Username bob;
    private AccountId aliceAccountId;
    private RequestSpecification webAdminApi;

    public abstract void awaitDocumentsIndexed(AccountId accountId, Long documentCount);

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        String suffix = UUID.randomUUID().toString();
        alice = Username.fromLocalPartWithDomain("alice-" + suffix, DOMAIN);
        bob = Username.fromLocalPartWithDomain("bob-" + suffix, DOMAIN);
        aliceAccountId = AccountId.fromUsername(alice);

        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(alice.asString(), PASSWORD)
            .addUser(bob.asString(), PASSWORD);

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
            .index(aliceAccountId, contactFields);

        awaitDocumentsIndexed(aliceAccountId, 1L);

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + alice.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .body("additionalInformation.status.ContactUserDataDeletionTaskStep", Matchers.is("DONE"));

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(server.getProbe(JmapGuiceContactAutocompleteProbe.class).list(aliceAccountId))
                .isEmpty());
    }

    @Test
    void shouldDeletePGPPublicKeys(GuiceJamesServer server) throws Exception {
        byte[] publicKeyPayload = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        JmapGuiceKeystoreManagerProbe keystoreManagerProbe = server.getProbe(JmapGuiceKeystoreManagerProbe.class);
        keystoreManagerProbe.save(alice, publicKeyPayload);

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + alice.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .body("additionalInformation.status.PGPKeysUserDeletionTaskStep", Matchers.is("DONE"));

        assertThat(keystoreManagerProbe.getKeyPayLoads(alice))
            .isEmpty();
    }

    @Test
    void shouldDeleteLabels(GuiceJamesServer server) {
        JmapGuiceLabelProbe labelProbe = server.getProbe(JmapGuiceLabelProbe.class);
        labelProbe.addLabel(alice, new LabelCreationRequest(new DisplayName("Important"), Option.empty(), Option.empty(), false));

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + alice.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .body("additionalInformation.status.LabelUserDeletionTaskStep", Matchers.is("DONE"));

        assertThat(labelProbe.listLabels(alice))
            .isEmpty();
    }

    @Test
    void shouldDeleteJmapSettings(GuiceJamesServer server) {
        JmapSettingsProbe settingsProbe = server.getProbe(JmapSettingsProbe.class);

        settingsProbe.reset(alice, Map.of("key", "value"));

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + alice.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .body("additionalInformation.status.JmapSettingsUserDeletionTaskStep", Matchers.is("DONE"));

        assertThat(settingsProbe.get(alice)).isNull();
    }

    @Test
    void shouldDeletePublicAssets(GuiceJamesServer server) {
        PublicAssetProbe publicAssetProbe = server.getProbe(PublicAssetProbe.class);
        publicAssetProbe.create(alice, PublicAssetGetMethodContract.CREATION_REQUEST());

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + alice.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .body("additionalInformation.status.PublicAssetDeletionTaskStep", Matchers.is("DONE"));

        assertThat(CollectionConverters.asJava(publicAssetProbe.list(alice)))
            .isEmpty();
    }
}
