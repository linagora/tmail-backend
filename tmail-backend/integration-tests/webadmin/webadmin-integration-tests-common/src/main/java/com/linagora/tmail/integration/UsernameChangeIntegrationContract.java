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
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
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

import com.linagora.tmail.james.common.probe.JmapGuiceContactAutocompleteProbe;
import com.linagora.tmail.james.common.probe.JmapGuiceKeystoreManagerProbe;
import com.linagora.tmail.james.common.probe.JmapGuiceLabelProbe;
import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelCreationRequest;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import scala.Option;
import scala.collection.JavaConverters;

public abstract class UsernameChangeIntegrationContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    private static final String DOMAIN = "linagora.com";
    private static final String PASSWORD = "123456";

    private Username alice;
    private Username bob;
    private AccountId aliceAccountId;
    private AccountId bobAccountId;
    private RequestSpecification webAdminApi;

    public abstract void awaitDocumentsIndexed(AccountId accountId, Long documentCount);

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        String suffix = UUID.randomUUID().toString();
        alice = Username.fromLocalPartWithDomain("alice-" + suffix, DOMAIN);
        bob = Username.fromLocalPartWithDomain("bob-" + suffix, DOMAIN);
        aliceAccountId = AccountId.fromUsername(alice);
        bobAccountId = AccountId.fromUsername(bob);

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
    void shouldAdaptContacts(GuiceJamesServer server) throws Exception {
        ContactFields contactFields = new ContactFields(new MailAddress("andre@linagora.com"), "Andre", "Dupont");

        JmapGuiceContactAutocompleteProbe contactProbe = server.getProbe(JmapGuiceContactAutocompleteProbe.class);
        EmailAddressContact contact = contactProbe.index(aliceAccountId, contactFields);

        awaitDocumentsIndexed(aliceAccountId, 1L);

        String taskId = webAdminApi
            .queryParam("action", "rename")
            .post("/users/" + alice.asString() + "/rename/" + bob.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(contactProbe.list(bobAccountId))
                    .containsOnly(contact);
                assertThat(contactProbe.list(aliceAccountId))
                    .isEmpty();
            });
    }

    @Test
    void shouldMigratePGPPublicKeys(GuiceJamesServer server) throws Exception {
        byte[] publicKeyPayload = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        JmapGuiceKeystoreManagerProbe keystoreManagerProbe = server.getProbe(JmapGuiceKeystoreManagerProbe.class);
        keystoreManagerProbe.save(alice, publicKeyPayload);

        String taskId = webAdminApi
            .queryParam("action", "rename")
            .post("/users/" + alice.asString() + "/rename/" + bob.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        assertThat(keystoreManagerProbe.getKeyPayLoads(bob))
            .containsOnly(publicKeyPayload);
        assertThat(keystoreManagerProbe.getKeyPayLoads(alice))
            .isEmpty();
    }

    @Test
    void shouldMigrateLabels(GuiceJamesServer server) {
        JmapGuiceLabelProbe labelProbe = server.getProbe(JmapGuiceLabelProbe.class);

        Label label = labelProbe.addLabel(alice, new LabelCreationRequest(new DisplayName("Important"), Option.empty(), Option.empty(), false));

        String taskId = webAdminApi
            .queryParam("action", "rename")
            .post("/users/" + alice.asString() + "/rename/" + bob.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("additionalInformation.status.LabelUsernameChangeTaskStep", Matchers.is("DONE"));

        assertThat(labelProbe.listLabels(bob))
            .containsExactly(label);
    }

    @Test
    void shouldMigrateJmapSettings(GuiceJamesServer server) {
        JmapSettingsProbe settingsProbe = server.getProbe(JmapSettingsProbe.class);

        settingsProbe.reset(alice, Map.of("key", "value"));

        String taskId = webAdminApi
            .queryParam("action", "rename")
            .post("/users/" + alice.asString() + "/rename/" + bob.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("additionalInformation.status.JmapSettingsUsernameChangeTaskStep", Matchers.is("DONE"));

        assertThat(JavaConverters.asJava(settingsProbe.get(bob).settings())
            .entrySet()
            .stream()
            .map(entry -> Map.entry(entry.getKey().asString(), entry.getValue().value()))
            .collect(Collectors.toSet()))
            .containsExactly(Map.entry("key", "value"));
    }
}
