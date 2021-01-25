package com.linagora.openpaas.deployment;

import static io.restassured.RestAssured.given;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import io.restassured.authentication.PreemptiveBasicAuthScheme;
import io.restassured.http.ContentType;

public interface JmapContract {
    String ARGUMENTS = "methodResponses[0][1]";
    Username BOB = Username.of("bob@domain.tld");
    String BOB_PASSWORD = "bobpassword";

    GenericContainer<?> jmapContainer();

    @BeforeEach
    default void setUp() throws Exception {
        Thread.sleep(2000);

        jmapContainer().execInContainer("james-cli", "AddDomain", "domain.tld");
        jmapContainer().execInContainer("james-cli", "AddUser", BOB.asString(), BOB_PASSWORD);

        PreemptiveBasicAuthScheme authScheme = new PreemptiveBasicAuthScheme();
        authScheme.setUserName(BOB.asString());
        authScheme.setPassword(BOB_PASSWORD);
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapContainer().getMappedPort(80))
            .setAccept(ContentType.JSON + "; jmapVersion=rfc-8621")
            .setAuth(authScheme)
            .build();
   }

   @Test
   default void mailboxGetTest() {
       List<String> expectedMailboxes = ImmutableList.<String>builder()
           .addAll(DefaultMailboxes.DEFAULT_MAILBOXES)
           .build();

       given()
           .body("{" +
               "  \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\"]," +
               "  \"methodCalls\": [[" +
               "    \"Mailbox/get\"," +
               "    {" +
               "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
               "      \"ids\": null" +
               "    }," +
               "    \"c1\"]]" +
               "}")
       .when()
           .post("/jmap")
       .then()
           .log().ifValidationFails()
           .statusCode(200)
           .body(ARGUMENTS + ".list", hasSize(6))
           .body(ARGUMENTS + ".list.name", hasItems(expectedMailboxes.toArray()));
   }

}
