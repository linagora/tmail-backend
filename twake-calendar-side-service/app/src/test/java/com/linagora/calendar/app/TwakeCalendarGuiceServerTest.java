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

package com.linagora.calendar.app;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.util.Port;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import com.google.inject.name.Names;
import com.linagora.calendar.app.modules.CalendarDataProbe;
import com.linagora.calendar.app.modules.MemoryAutoCompleteModule;
import com.linagora.calendar.restapi.RestApiServerProbe;
import com.linagora.calendar.storage.OpenPaaSId;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

class TwakeCalendarGuiceServerTest  {
    public static final Domain DOMAIN = Domain.of("linagora.com");
    public static final String PASSWORD = "secret";
    public static final Username USERNAME = Username.of("btellier@linagora.com");
    private static final String USERINFO_TOKEN_URI_PATH = "/token/introspect";

    private static ClientAndServer mockServer = ClientAndServer.startClientAndServer(0);

    private static URL getUserInfoTokenEndpoint() {
        try {
            return new URI(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), USERINFO_TOKEN_URI_PATH)).toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private OpenPaaSId userId;

    @RegisterExtension
    static TwakeCalendarExtension twakeCalendarExtension = new TwakeCalendarExtension(
        TwakeCalendarConfiguration.builder()
            .configurationFromClasspath()
            .userChoice(TwakeCalendarConfiguration.UserChoice.MEMORY)
            .dbChoice(TwakeCalendarConfiguration.DbChoice.MEMORY),
        binder -> binder.bind(URL.class).annotatedWith(Names.named("userInfo")).toProvider(TwakeCalendarGuiceServerTest::getUserInfoTokenEndpoint));

    @BeforeEach
    void setUp(TwakeCalendarGuiceServer server) {
        Port port = server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort();

        RestAssured.requestSpecification =  new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(port.getValue())
            .setBasePath("/")
            .build();

        userId = server.getProbe(CalendarDataProbe.class)
            .addUser(USERNAME, PASSWORD);
    }

    @Test
    void shouldExposeWebAdminHealthcheck() {
        String body = given()
        .when()
            .get("/healthcheck")
        .then()
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
            {
                "status": "healthy",
                "checks": [
                    {
                        "componentName": "Guice application lifecycle",
                        "escapedComponentName": "Guice%20application%20lifecycle",
                        "status": "healthy",
                        "cause": null
                    }
                ]
            }""");
    }

    @Test
    void shouldExposeWebAdminDomains() {
        String body = given()
        .when()
            .get("/domains")
        .then()
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
            ["linagora.com"]""");
    }

    @Test
    void shouldExposeWebAdminUsers() {
        String body = given()
        .when()
            .get("/users")
        .then()
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
            [{"username":"btellier@linagora.com"}]""");
    }

    @Test
    void shouldExposeMetrics() {
        String body = given()
        .when()
            .get("/metrics")
        .then()
            .extract()
            .body()
            .asString();

        assertThat(body).contains("jvm_threads_runnable_count");
    }

    @Test
    void shouldExposeCalendarRestApi(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .when()
            .get("/api/themes/anything")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("{\"logos\":{},\"colors\":{}}");
    }

    @Test
    void shouldGenerateTokens(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .when()
            .post("/api/jwt/generate")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThat(body).startsWith("\"eyJ");
    }

    @Test
    void shouldAuthenticateWithTokens(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .when()
            .post("/api/jwt/generate")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
        String unquoted = body.substring(1, body.length() - 1);

        given()
            .header("Authorization", "Bearer " + unquoted)
        .when()
            .get("/api/themes/anything")
        .then()
            .statusCode(200);
    }

    @Test
    void shouldAuthenticateWithOidc(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);
        String activeResponse = "{" +
            "    \"exp\": 1652868271," +
            "    \"nbf\": 0," +
            "    \"iat\": 1652867971," +
            "    \"jti\": \"41ee3cc3-b908-4870-bff2-34b895b9fadf\"," +
            "    \"aud\": \"account\"," +
            "    \"typ\": \"Bearer\"," +
            "    \"acr\": \"1\"," +
            "    \"scope\": \"email\"," +
            "    \"email\": \"btellier@linagora.com\"," +
            "    \"active\": true" +
            "}";
        updateMockerServerSpecifications(activeResponse, 200);

        given()
            .header("Authorization", "Bearer oidc_opac_token")
        .when()
            .get("/api/themes/anything")
        .then()
            .statusCode(200);
    }

    @Test
    void shouldExposeLogoEndpoint(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .when()
            .get("/api/themes/anything/logo")
        .then()
            .statusCode(200); // Follows the redirect;
    }

    @Test
    void shouldRejectNonExistingUsers(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        given()
            .auth().basic("notFound@linagora.com", PASSWORD)
            .when()
            .get("/api/themes/anything/logo")
        .then()
            .statusCode(401);
    }

    @Test
    void shouldRejectBadPassword(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        given()
            .auth().basic(USERNAME.asString(), "notGood")
            .when()
            .get("/api/themes/anything/logo")
        .then()
            .statusCode(401);
    }

    @Test
    void shouldRejectWhenNoAuth(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        given()
            .when()
            .get("/api/themes/anything/logo")
        .then()
            .statusCode(401);
    }

    @Test
    void shouldServeDavConfiguration(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .body("[{\"name\":\"core\",\"keys\":[\"davserver\"]}]")
        .when()
            .post("/api/configurations")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
    [
    {"name":"core","configurations":[
     {"name":"davserver",
       "value":{
         "backend":{"url":"https://dav.linagora.com"},
         "frontend":{"url":"https://dav.linagora.com"}
        }
       }
      ]
     }
    ]""");
    }

    @Test
    void shouldServeHomePageConfiguration(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .body("[{\"name\":\"core\",\"keys\":[\"homePage\"]}]")
        .when()
            .post("/api/configurations")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
    [{"name":"core","configurations":[{"name":"homePage","value": null}]}]""");
    }

    @Test
    void shouldServeAllowDomainAdminToManageUserEmailsConfiguration(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .body("[{\"name\":\"core\",\"keys\":[\"allowDomainAdminToManageUserEmails\"]}]")
        .when()
            .post("/api/configurations")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
    [{"name":"core","configurations":[{"name":"allowDomainAdminToManageUserEmails","value": null}]}]""");
    }

    @Test
    void shouldServeAllowCalendarSharingConfiguration(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .body("[{\"name\":\"linagora.esn.calendar\",\"keys\":[\"features\"]}]")
        .when()
            .post("/api/configurations")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
    [{"name":"linagora.esn.calendar","configurations":[{"name":"features","value":{"isSharingCalendarEnabled": true}}]}]""");
    }

    @Test
    void shouldServeAllowJitsiConfiguration(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .body("[{\"name\":\"linagora.esn.videoconference\",\"keys\":[\"jitsiInstanceUrl\",\"openPaasVideoconferenceAppUrl\"]}]")
        .when()
            .post("/api/configurations")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
    [{"name":"linagora.esn.videoconference","configurations":[{"name":"jitsiInstanceUrl","value":"https://jitsi.linagora.com"},{"name":"openPaasVideoconferenceAppUrl","value":"https://jitsi.linagora.com"}]}]""");
    }

    @Test
    void shouldServeAllowContactsConfiguration(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .body("[{\"name\":\"linagora.esn.contacts\",\"keys\":[\"features\"]}]")
        .when()
            .post("/api/configurations")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
    [{"name":"linagora.esn.contacts","configurations":[{"name":"features","value":{"isVirtualFollowingAddressbookEnabled":false,"isSharingAddressbookEnabled":true,"isVirtualUserAddressbookEnabled":false,"isDomainMembersAddressbookEnabled":true}}]}]""");
    }

    @Test
    void shouldServeLegacyCalendarConfiguration(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .body("[{\"name\":\"linagora.esn.calendar\",\"keys\":[\"hideDeclinedEvents\",\"workingDays\"]}]")
        .when()
            .post("/api/configurations")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
    [{"name":"linagora.esn.calendar","configurations":[{"name":"hideDeclinedEvents","value":null},{"name":"workingDays", "value":null}]}]""");
    }

    @Test
    void shouldServeDefaultCoreConfiguration(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .body("[{\"name\":\"core\",\"keys\":[\"language\",\"businessHours\",\"datetime\"]}]")
        .when()
            .post("/api/configurations")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
    [{"name":"core","configurations":[{"name":"language","value":"en"},{"name":"businessHours","value":[{"start":"8:0","end":"19:0","daysOfWeek":[1,2,3,4,5]}]},{"name":"datetime","value":{"timeZone":"Europe/Paris","use24hourFormat":true}}]}]""");
    }

    @Test
    void shouldServeAvatars(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        given()
            .queryParam("email", "btellier@linagora.com")
        .when()
            .get("/api/avatars")
        .then()
            .statusCode(200)
            .header("Content-Type", "image/png");
    }

    @Test
    void shouldSupportPeopleSearch(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        server.getProbe(MemoryAutoCompleteModule.Probe.class)
            .add(USERNAME.asString(), "grepme@linagora.com", "Grep", "Me");

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .body("{\"q\":\"grepm\",\"objectTypes\":[\"user\",\"group\",\"contact\",\"ldap\"],\"limit\":10}")
        .when()
            .post("/api/people/search")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body)
            .whenIgnoringPaths("[0].id")
            .isEqualTo("""
    [{
      "id":"f0b97959-1f62-40f7-9df0-798125d62308",
      "objectType":"contact",
      "emailAddresses":[{"value":"grepme@linagora.com","type":"Work"}],
      "phoneNumbers":[],
      "names":[{"displayName":"Grep Me","type":"default"}],
      "photos":[{"url":"https://twcalendar.linagora.com/api/avatars?email=grepme@linagora.com","type":"default"}]
    }]""");
    }

    @Test
    void peopleSearchShouldIgnoreUnknownFields(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        server.getProbe(MemoryAutoCompleteModule.Probe.class)
            .add(USERNAME.asString(), "grepme@linagora.com", "Grep", "Me");

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .body("{\"q\":\"grepm\",\"objectTypes\":[\"user\",\"group\",\"contact\",\"ldap\"],\"limit\":10,\"unknown\":\"whatever\"}")
        .when()
            .post("/api/people/search")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body)
            .whenIgnoringPaths("[0].id")
            .isEqualTo("""
    [{
      "id":"f0b97959-1f62-40f7-9df0-798125d62308",
      "objectType":"contact",
      "emailAddresses":[{"value":"grepme@linagora.com","type":"Work"}],
      "phoneNumbers":[],
      "names":[{"displayName":"Grep Me","type":"default"}],
      "photos":[{"url":"https://twcalendar.linagora.com/api/avatars?email=grepme@linagora.com","type":"default"}]
    }]""");
    }

    @Test
    void shouldSupportProfileAvatar(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        given()
            .redirects().follow(false)
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
        .when()
            .get("/api/users/" + userId.value() + "/profile/avatar")
        .then()
            .statusCode(302)
            .header("Location", "https://twcalendar.linagora.com/api/avatars?email=btellier@linagora.com");
    }

    @Test
    void shouldSupportDomainEndpoint(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String domainId = server.getProbe(CalendarDataProbe.class).domainId(DOMAIN).value();
        String body = given()
            .redirects().follow(false)
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
        .when()
            .get("/api/domains/" + domainId)
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body)
            .isEqualTo(String.format("""
                {
                     "timestamps": {
                         "creation": "1970-01-01T00:00:00.000Z"
                     },
                     "hostnames": ["linagora.com"],
                     "schemaVersion": 1,
                     "_id": "%s",
                     "name": "linagora.com",
                     "company_name": "linagora.com",
                     "administrators": [ ],
                     "injections": [],
                     "__v": 0
                 }
                """, domainId));
    }

    @Test
    void shouldSupportUserEndpoint(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String domainId = server.getProbe(CalendarDataProbe.class).domainId(DOMAIN).value();
        String userId = server.getProbe(CalendarDataProbe.class).userId(USERNAME).value();
        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
        .when()
            .get("/api/users/" + userId)
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body)
            .isEqualTo(String.format("""
                {
                    "preferredEmail": "btellier@linagora.com",
                    "_id": "%s",
                    "state": [],
                    "domains": [
                        {
                            "domain_id": "%s",
                            "joined_at": "1970-01-01T00:00:00.000Z"
                        }
                    ],
                    "main_phone": "",
                    "followings": 0,
                    "following": false,
                    "followers": 0,
                    "emails": [
                        "btellier@linagora.com"
                    ],
                    "firstname": "btellier@linagora.com",
                    "lastname": "btellier@linagora.com",
                    "objectType": "user"
                }
                """, userId, domainId));
    }

    @Test
    void shouldSupportUserByEmailEndpoint(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String domainId = server.getProbe(CalendarDataProbe.class).domainId(DOMAIN).value();
        String userId = server.getProbe(CalendarDataProbe.class).userId(USERNAME).value();
        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .queryParam("email", "btellier@linagora.com")
        .when()
            .get("/api/users")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body)
            .isEqualTo(String.format("""
                [{
                    "preferredEmail": "btellier@linagora.com",
                    "_id": "%s",
                    "state": [],
                    "domains": [
                        {
                            "domain_id": "%s",
                            "joined_at": "1970-01-01T00:00:00.000Z"
                        }
                    ],
                    "main_phone": "",
                    "followings": 0,
                    "following": false,
                    "followers": 0,
                    "emails": [
                        "btellier@linagora.com"
                    ],
                    "firstname": "btellier@linagora.com",
                    "lastname": "btellier@linagora.com",
                    "objectType": "user"
                }]
                """, userId, domainId));
    }

    @Test
    void userByEmailEndpointShouldReturnEmptyWHenNotFound(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .queryParam("email", "notFOund@linagora.com")
        .when()
            .get("/api/users")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body)
            .isEqualTo("[]");
    }

    @Test
    void getUserShouldReturnProfile(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);
        String domainId = server.getProbe(CalendarDataProbe.class).domainId(DOMAIN).value();
        String userId = server.getProbe(CalendarDataProbe.class).userId(USERNAME).value();

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
        .when()
            .get("/api/user")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body)
            .isEqualTo(String.format("""
                {
                    "id": "%s",
                    "_id": "%s",
                    "accounts": [
                        {
                            "hosted": true,
                            "preferredEmailIndex": 0,
                            "type": "email",
                            "timestamps": {
                                "creation": "1970-01-01T00:00:00.000Z"
                            },
                            "emails": [
                                "btellier@linagora.com"
                            ]
                        }
                    ],
                    "isPlatformAdmin": false,
                    "login": {
                        "success": "1970-01-01T00:00:00.000Z",
                        "failures": []
                    },
                    "configurations": { "modules" : [
                        {
                            "name": "core",
                            "configurations": [
                                {
                                    "name": "davserver",
                                    "value": {
                                        "frontend": {
                                            "url": "https://dav.linagora.com"
                                        },
                                        "backend": {
                                            "url": "https://dav.linagora.com"
                                        }
                                    }
                                },
                                {
                                    "name": "allowDomainAdminToManageUserEmails",
                                    "value": null
                                },
                                {
                                    "name": "homePage",
                                    "value": null
                                },
                                {
                                    "name": "language",
                                    "value": "en"
                                },
                                {
                                    "name": "datetime",
                                    "value": {
                                        "timeZone": "Europe/Paris",
                                        "use24hourFormat": true
                                    }
                                },
                                {
                                    "name": "businessHours",
                                    "value": [
                                        {
                                            "start": "8:0",
                                            "end": "19:0",
                                            "daysOfWeek": [
                                                1,
                                                2,
                                                3,
                                                4,
                                                5
                                            ]
                                        }
                                    ]
                                }
                            ]
                        },
                        {
                            "name": "linagora.esn.calendar",
                            "configurations": [
                                {
                                    "name": "features",
                                    "value": {
                                        "isSharingCalendarEnabled": true
                                    }
                                },
                                {
                                    "name": "workingDays",
                                    "value": null
                                },
                                {
                                    "name": "hideDeclinedEvents",
                                    "value": null
                                }
                            ]
                        },
                        {
                            "name": "linagora.esn.videoconference",
                            "configurations": [
                                {
                                    "name": "jitsiInstanceUrl",
                                    "value": "https://jitsi.linagora.com"
                                },
                                {
                                    "name": "openPaasVideoconferenceAppUrl",
                                    "value": "https://jitsi.linagora.com"
                                }
                            ]
                        },
                        {
                            "name": "linagora.esn.contacts",
                            "configurations": [
                                {
                                    "name": "features",
                                    "value": {
                                        "isVirtualFollowingAddressbookEnabled": false,
                                        "isVirtualUserAddressbookEnabled": false,
                                        "isSharingAddressbookEnabled": true,
                                        "isDomainMembersAddressbookEnabled": true
                                    }
                                }
                            ]
                        }
                    ]},
                    "preferredEmail": "btellier@linagora.com",
                    "state": [],
                    "domains": [
                        {
                            "domain_id": "%s",
                            "joined_at": "1970-01-01T00:00:00.000Z"
                        }
                    ],
                    "main_phone": "",
                    "followings": 0,
                    "following": false,
                    "followers": 0,
                    "emails": [
                        "btellier@linagora.com"
                    ],
                    "firstname": "btellier@linagora.com",
                    "lastname": "btellier@linagora.com",
                    "objectType": "user"
                }""", userId, userId, domainId));
    }

    @Test
    void userShouldReturnNotFoundWhenNone(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String domainId = server.getProbe(CalendarDataProbe.class).domainId(DOMAIN).value();

        given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
        .when()
            .get("/api/users/" + domainId)
        .then()
            .statusCode(404);
    }

    @Test
    void domainShouldReturnNotFoundWhenNone(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String domainId = server.getProbe(CalendarDataProbe.class).domainId(DOMAIN).value();
        String userId = server.getProbe(CalendarDataProbe.class).userId(USERNAME).value();

        given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
        .when()
            .get("/api/domains/" + userId)
        .then()
            .statusCode(404);
    }

    private static void targetRestAPI(TwakeCalendarGuiceServer server) {
        RestAssured.requestSpecification =  new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(server.getProbe(RestApiServerProbe.class).getPort().getValue())
            .setBasePath("/")
            .build();
    }

    private void updateMockerServerSpecifications(String response, int statusResponse) {
        mockServer
            .when(HttpRequest.request().withPath(USERINFO_TOKEN_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(statusResponse)
                .withHeader("Content-Type", "application/json")
                .withBody(response, StandardCharsets.UTF_8));
    }
}
