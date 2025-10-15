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

package com.linagora.tmail.webadmin;

import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.EMPTY_RATE_LIMIT;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_RECEIVED_PER_DAYS_UNLIMITED;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_RECEIVED_PER_HOURS_UNLIMITED;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_RECEIVED_PER_MINUTE_UNLIMITED;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_SENT_PER_DAYS_UNLIMITED;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_SENT_PER_HOURS_UNLIMITED;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_SENT_PER_MINUTE_UNLIMITED;
import static com.linagora.tmail.webadmin.RateLimitsUserRoutesTest.GET_RATE_LIMITS_OF_USER_PATH;
import static com.linagora.tmail.webadmin.RateLimitsUserRoutesTest.PUT_RATE_LIMITS_TO_USER_PATH;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.stream.Stream;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;

public class RateLimitsDomainRoutesTest {
    private static final String PUT_RATE_LIMITS_TO_DOMAIN_PATH = "/domains/%s/ratelimits";
    private static final String GET_RATE_LIMITS_OF_DOMAIN_PATH = "/domains/%s/ratelimits";
    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Domain NOT_EXISTING_DOMAIN = Domain.of("domain2.tld");
    private static final RateLimitingDefinition UNLIMITED_RATE_LIMITS = RateLimitingDefinition.builder()
        .mailsSentPerMinute(MAILS_SENT_PER_MINUTE_UNLIMITED)
        .mailsSentPerHours(MAILS_SENT_PER_HOURS_UNLIMITED)
        .mailsSentPerDays(MAILS_SENT_PER_DAYS_UNLIMITED)
        .mailsReceivedPerMinute(MAILS_RECEIVED_PER_MINUTE_UNLIMITED)
        .mailsReceivedPerHours(MAILS_RECEIVED_PER_HOURS_UNLIMITED)
        .mailsReceivedPerDays(MAILS_RECEIVED_PER_DAYS_UNLIMITED)
        .build();
    private static final RateLimitingDefinition LIMITED_RATE_LIMITS = RateLimitingDefinition.builder()
        .mailsSentPerMinute(10L)
        .mailsSentPerHours(100L)
        .mailsSentPerDays(1000L)
        .mailsReceivedPerMinute(20L)
        .mailsReceivedPerHours(200L)
        .mailsReceivedPerDays(2000L)
        .build();
    private static final String UNLIMITED_PAYLOAD = """
        {
            "mailsSentPerMinute": -1,
            "mailsSentPerHours": -1,
            "mailsSentPerDays": -1,
            "mailsReceivedPerMinute": -1,
            "mailsReceivedPerHours": -1,
            "mailsReceivedPerDays": -1
        }""";
    private static final String LIMITED_PAYLOAD = """
        {
            "mailsSentPerMinute": 10,
            "mailsSentPerHours": 100,
            "mailsSentPerDays": 1000,
            "mailsReceivedPerMinute": 20,
            "mailsReceivedPerHours": 200,
            "mailsReceivedPerDays": 2000
        }""";

    private static Stream<Arguments> domainInvalidSource() {
        return Stream.of(
            Arguments.of("@.tld"),
            Arguments.of("aa@aa@aa.tld"));
    }

    private WebAdminServer webAdminServer;
    private UsersRepository usersRepository;
    private MemoryDomainList domainList;
    private RateLimitingRepository rateLimitingRepository;

    @BeforeEach
    void setUp() throws Exception {
        rateLimitingRepository = new MemoryRateLimitingRepository();
        usersRepository = mock(UsersRepository.class);
        DNSService dnsService = mock(DNSService.class);
        domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(DOMAIN);
        RateLimitsUserRoutes rateLimitsUserRoutes = new RateLimitsUserRoutes(rateLimitingRepository, usersRepository, new JsonTransformer());
        RateLimitsDomainRoutes rateLimitsDomainRoutes = new RateLimitsDomainRoutes(rateLimitingRepository, domainList, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(rateLimitsUserRoutes, rateLimitsDomainRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        when(usersRepository.contains(BOB)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class ApplyRateLimitsToDomainTest {
        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.RateLimitsDomainRoutesTest#domainInvalidSource")
        void shouldReturnErrorWhenInvalidDomain(String domain) {
            Map<String, Object> errors = given()
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, domain))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            Assertions.assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request");
        }

        @Test
        void shouldReturnNotFoundWhenDomainNotFound() {
            Map<String, Object> errors = given()
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, NOT_EXISTING_DOMAIN.asString()))
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            Assertions.assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "Domain " + NOT_EXISTING_DOMAIN.asString() + " does not exist");
        }

        @Test
        void shouldReturnDeserializingErrorWhenBadPayload() {
            Map<String, Object> errors = given()
                .body("""
                {
                    "badPayload": "bad"
                }""")
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            Assertions.assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Error while deserializing applyRateLimitsToDomain request");
        }

        @Test
        void shouldSucceed() {
            given()
                .body(LIMITED_PAYLOAD)
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Mono.from(rateLimitingRepository.getRateLimiting(DOMAIN)).block())
                .isEqualTo(LIMITED_RATE_LIMITS);
        }

        @Test
        void shouldNotConflictWithUserRateLimitRoute() {
            given()
                .body(LIMITED_PAYLOAD)
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            given()
                .body(UNLIMITED_PAYLOAD)
                .put(String.format(PUT_RATE_LIMITS_TO_USER_PATH, BOB.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Mono.from(rateLimitingRepository.getRateLimiting(DOMAIN)).block())
                .isEqualTo(LIMITED_RATE_LIMITS);
            assertThat(Mono.from(rateLimitingRepository.getRateLimiting(BOB)).block())
                .isEqualTo(UNLIMITED_RATE_LIMITS);
        }

        @Test
        void nullValuesShouldBeAllowed() {
            given()
                .body("""
                {
                    "mailsSentPerMinute": null,
                    "mailsSentPerHours": null,
                    "mailsSentPerDays": null,
                    "mailsReceivedPerMinute": null,
                    "mailsReceivedPerHours": null,
                    "mailsReceivedPerDays": null
                }""")
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Mono.from(rateLimitingRepository.getRateLimiting(DOMAIN)).block())
                .isEqualTo(EMPTY_RATE_LIMIT);
        }

        @Test
        void unlimitedValuesShouldBeAllowed() {
            given()
                .body(UNLIMITED_PAYLOAD)
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Mono.from(rateLimitingRepository.getRateLimiting(DOMAIN)).block())
                .isEqualTo(UNLIMITED_RATE_LIMITS);
        }

        @Test
        void mixedTypesOfValuesShouldBeAllowed() {
            given()
                .body("""
                {
                    "mailsSentPerMinute": -1,
                    "mailsSentPerHours": 100,
                    "mailsSentPerDays": null,
                    "mailsReceivedPerMinute": 20,
                    "mailsReceivedPerHours": null,
                    "mailsReceivedPerDays": -1
                }""")
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Mono.from(rateLimitingRepository.getRateLimiting(DOMAIN)).block())
                .isEqualTo(RateLimitingDefinition.builder()
                    .mailsSentPerMinute(MAILS_SENT_PER_MINUTE_UNLIMITED)
                    .mailsSentPerHours(100L)
                    .mailsSentPerDays(null)
                    .mailsReceivedPerMinute(20L)
                    .mailsReceivedPerHours(null)
                    .mailsReceivedPerDays(MAILS_RECEIVED_PER_DAYS_UNLIMITED)
                    .build());
        }

        @Test
        void shouldOverridePreviousLimits() {
            given()
                .body(LIMITED_PAYLOAD)
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            given()
                .body("""
                {
                    "mailsSentPerMinute": 15,
                    "mailsSentPerHours": 150,
                    "mailsSentPerDays": 1500,
                    "mailsReceivedPerMinute": 25,
                    "mailsReceivedPerHours": 250,
                    "mailsReceivedPerDays": 2500
                }""")
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Mono.from(rateLimitingRepository.getRateLimiting(DOMAIN)).block())
                .isEqualTo(RateLimitingDefinition.builder()
                    .mailsSentPerMinute(15L)
                    .mailsSentPerHours(150L)
                    .mailsSentPerDays(1500L)
                    .mailsReceivedPerMinute(25L)
                    .mailsReceivedPerHours(250L)
                    .mailsReceivedPerDays(2500L)
                    .build());
        }

        @Test
        void shouldBeIdempotent() {
            given()
                .body(LIMITED_PAYLOAD)
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            given()
                .body(LIMITED_PAYLOAD)
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Mono.from(rateLimitingRepository.getRateLimiting(DOMAIN)).block())
                .isEqualTo(LIMITED_RATE_LIMITS);
        }

        @Test
        void shouldBeAbleToOmitValues() {
            given()
                .body("""
                {
                    "mailsSentPerHours": 100,
                    "mailsSentPerDays": 1000,
                    "mailsReceivedPerHours": 200,
                    "mailsReceivedPerDays": 2000
                }""")
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Mono.from(rateLimitingRepository.getRateLimiting(DOMAIN)).block())
                .isEqualTo(RateLimitingDefinition.builder()
                    .mailsSentPerMinute(null)
                    .mailsSentPerHours(100L)
                    .mailsSentPerDays(1000L)
                    .mailsReceivedPerMinute(null)
                    .mailsReceivedPerHours(200L)
                    .mailsReceivedPerDays(2000L)
                    .build());
        }

        @Test
        void emptyPayloadShouldResetRateLimits() {
            given()
                .body(LIMITED_PAYLOAD)
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            given()
                .body("{}")
                .put(String.format(PUT_RATE_LIMITS_TO_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Mono.from(rateLimitingRepository.getRateLimiting(DOMAIN)).block())
                .isEqualTo(EMPTY_RATE_LIMIT);
        }
    }

    @Nested
    class GetRateLimitsOfDomainTest {
        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.RateLimitsDomainRoutesTest#domainInvalidSource")
        void shouldReturnErrorWhenInvalidDomain(String domain) {
            Map<String, Object> errors = given()
                .put(String.format(GET_RATE_LIMITS_OF_DOMAIN_PATH, domain))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            Assertions.assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request");
        }

        @Test
        void shouldReturnNotFoundWhenDomainNotFound() {
            Map<String, Object> errors = given()
                .put(String.format(GET_RATE_LIMITS_OF_DOMAIN_PATH, NOT_EXISTING_DOMAIN.asString()))
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            Assertions.assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "Domain " + NOT_EXISTING_DOMAIN.asString() + " does not exist");
        }

        @Test
        void shouldSucceed() {
            Mono.from(rateLimitingRepository.setRateLimiting(DOMAIN, LIMITED_RATE_LIMITS)).block();

            String response = given()
                .get(String.format(GET_RATE_LIMITS_OF_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .isEqualTo(LIMITED_PAYLOAD);
        }

        @Test
        void shouldNotConflictWithUserRateLimitRoute() {
            Mono.from(rateLimitingRepository.setRateLimiting(DOMAIN, LIMITED_RATE_LIMITS)).block();
            Mono.from(rateLimitingRepository.setRateLimiting(BOB, UNLIMITED_RATE_LIMITS)).block();

            String domainLimits = given()
                .get(String.format(GET_RATE_LIMITS_OF_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            String userLimits = given()
                .get(String.format(GET_RATE_LIMITS_OF_USER_PATH, BOB.asString()))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(domainLimits).isEqualTo(LIMITED_PAYLOAD);
            assertThatJson(userLimits).isEqualTo(UNLIMITED_PAYLOAD);
        }

        @Test
        void shouldReturnUnlimitedLimits() {
            Mono.from(rateLimitingRepository.setRateLimiting(DOMAIN, UNLIMITED_RATE_LIMITS)).block();

            String response = given()
                .get(String.format(GET_RATE_LIMITS_OF_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .isEqualTo(UNLIMITED_PAYLOAD);
        }

        @Test
        void shouldReturnNullLimitsByDefault() {
            String response = given()
                .get(String.format(GET_RATE_LIMITS_OF_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .isEqualTo("""
                {
                    "mailsSentPerMinute": null,
                    "mailsSentPerHours": null,
                    "mailsSentPerDays": null,
                    "mailsReceivedPerMinute": null,
                    "mailsReceivedPerHours": null,
                    "mailsReceivedPerDays": null
                }""");
        }

        @Test
        void shouldReturnMixedTypesOfLimits() {
            Mono.from(rateLimitingRepository.setRateLimiting(DOMAIN, RateLimitingDefinition.builder()
                .mailsSentPerMinute(MAILS_SENT_PER_MINUTE_UNLIMITED)
                .mailsSentPerHours(100L)
                .mailsSentPerDays(null)
                .mailsReceivedPerMinute(20L)
                .mailsReceivedPerHours(null)
                .mailsReceivedPerDays(MAILS_RECEIVED_PER_DAYS_UNLIMITED)
                .build())).block();

            String response = given()
                .get(String.format(GET_RATE_LIMITS_OF_DOMAIN_PATH, DOMAIN.asString()))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .isEqualTo("""
                {
                    "mailsSentPerMinute": -1,
                    "mailsSentPerHours": 100,
                    "mailsSentPerDays": null,
                    "mailsReceivedPerMinute": 20,
                    "mailsReceivedPerHours": null,
                    "mailsReceivedPerDays": -1
                }""");
        }
    }
}
