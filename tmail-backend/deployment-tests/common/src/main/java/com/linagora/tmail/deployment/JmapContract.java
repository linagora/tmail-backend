package com.linagora.tmail.deployment;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;
import io.restassured.authentication.NoAuthScheme;
import io.restassured.authentication.PreemptiveBasicAuthScheme;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public interface JmapContract {
    String ARGUMENTS = "methodResponses[0][1]";
    Username BOB = Username.of("bob@domain.tld");
    Username ALICE = Username.of("alice@domain.tld");
    String ALICE_ACCOUNT_ID = "3d537c9fe934639a6a2f33b8357827bfab186b178c0b4d8424173b0e6472b44e";
    String BOB_PASSWORD = "bobpassword";
    String ALICE_PASSWORD = "alicepassword";

    GenericContainer<?> jmapContainer();

    @BeforeEach
    default void setUp() throws Exception {
        jmapContainer().execInContainer("james-cli", "AddDomain", "domain.tld");
        jmapContainer().execInContainer("james-cli", "AddUser", BOB.asString(), BOB_PASSWORD);
        jmapContainer().execInContainer("james-cli", "AddUser", ALICE.asString(), ALICE_PASSWORD);

        PreemptiveBasicAuthScheme authScheme = new PreemptiveBasicAuthScheme();
        authScheme.setUserName(BOB.asString());
        authScheme.setPassword(BOB_PASSWORD);
        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setBaseUri("http://" + jmapContainer().getContainerIpAddress())
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
           .body(ARGUMENTS + ".list", hasSize(expectedMailboxes.size()))
           .body(ARGUMENTS + ".list.name", hasItems(expectedMailboxes.toArray()));
   }

   @Test
   default void sessionRouteAndUploadRouteAndDownloadRouteShouldWork() {
       RequestSpecification webadminRequestSpec = new RequestSpecBuilder()
           .setContentType(ContentType.JSON)
           .setAccept(ContentType.JSON)
           .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
           .setBaseUri("http://" + jmapContainer().getContainerIpAddress())
           .setPort(jmapContainer().getMappedPort(8000))
           .setAuth(new NoAuthScheme())
           .build();

       // ALICE delegates BOB
       given(webadminRequestSpec)
       .when()
           .put(String.format("/users/%s/authorizedUsers/%s", ALICE.asString(), BOB.asString()))
       .then()
           .statusCode(200);

       // BOB's JMAP session should return BOB and ALICE accounts
       String response = given()
       .when()
           .get("/jmap/session")
       .then()
           .statusCode(200)
           .extract()
           .body()
           .asString();

       assertThat(response).contains(BOB.asString(), ALICE.asString());

       // BOB upload as ALICE
       String blobId = given()
           .body("whatever")
       .when()
           .post(String.format("/upload/%s", ALICE_ACCOUNT_ID))
       .then()
           .statusCode(201)
           .body("blobId", Matchers.notNullValue())
           .body("accountId", is(ALICE_ACCOUNT_ID))
           .extract()
           .body()
           .jsonPath()
           .getString("blobId");

       // BOB download as ALICE
       given()
       .when()
           .get(String.format("/download/%s/%s", ALICE_ACCOUNT_ID, blobId))
       .then()
           .statusCode(200)
           .body(is("whatever"));
   }

}
