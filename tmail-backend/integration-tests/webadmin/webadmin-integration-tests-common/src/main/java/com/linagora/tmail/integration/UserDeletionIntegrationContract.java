/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package com.linagora.tmail.integration;

import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

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
import com.linagora.tmail.james.jmap.contact.ContactFields;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

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
}
