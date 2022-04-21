package com.linagora.tmail.webadmin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpHeader.LOCATION;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;

import io.restassured.RestAssured;
import net.javacrumbs.jsonunit.core.internal.Options;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class EmailAddressContactRoutesTest {
    private static final String DOMAINS_CONTACTS_PATH = "/domains/%s/contacts";
    private static final String ALL_DOMAINS_PATH = "/domains/contacts";
    private static final Domain CONTACT_DOMAIN = Domain.of("contact.com");

    private static final String mailAddressA = "john@" + CONTACT_DOMAIN.asString();
    private static final String firstnameA = "John";
    private static final String surnameA = "Carpenter";

    private static final String mailAddressB = "marie@" + CONTACT_DOMAIN.asString();
    private static final String firstnameB = "Marie";
    private static final String surnameB = "Carpenter";

    private static Stream<Arguments> domainInvalidSource() {
        return Stream.of(
            Arguments.of("Dom@in"),
            Arguments.of("@")
        );
    }

    private WebAdminServer webAdminServer;
    private InMemoryEmailAddressContactSearchEngine emailAddressContactSearchEngine;

    @BeforeEach
    void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(CONTACT_DOMAIN);

        emailAddressContactSearchEngine = new InMemoryEmailAddressContactSearchEngine();

        EmailAddressContactRoutes routes = new EmailAddressContactRoutes(emailAddressContactSearchEngine, domainList, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(routes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(String.format(DOMAINS_CONTACTS_PATH, CONTACT_DOMAIN.asString()))
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class GetContactsByDomainTest {
        @Test
        void getContactsByDomainShouldReturnEmptyByDefault() {
            List<String> contacts = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".");

            assertThat(contacts).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.EmailAddressContactRoutesTest#domainInvalidSource")
        void getContactsByDomainShouldReturnErrorWhenDomainInvalid(String domain) {
            Map<String, Object> errors = given()
                .basePath(String.format(DOMAINS_CONTACTS_PATH, domain))
                .get()
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "Domain parts ASCII chars must be a-z A-Z 0-9 - or _");
        }

        @Test
        void getContactsByDomainShouldReturnListEntryWhenHasSingleElement() throws Exception {
            ContactFields contactFields = new ContactFields(new MailAddress(mailAddressA), firstnameA, surnameA);
            Mono.from(emailAddressContactSearchEngine.index(CONTACT_DOMAIN, contactFields)).block();

            String response = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .isEqualTo("[" +
                    "\"" + mailAddressA + "\"" +
                    "]");
        }

        @Test
        void getContactsByDomainShouldReturnListEntryWhenHasMultipleElement() throws Exception {
            ContactFields contactFieldsA = new ContactFields(new MailAddress(mailAddressA), firstnameA, surnameA);
            Mono.from(emailAddressContactSearchEngine.index(CONTACT_DOMAIN, contactFieldsA)).block();

            ContactFields contactFieldsB = new ContactFields(new MailAddress(mailAddressB), firstnameB, surnameB);
            Mono.from(emailAddressContactSearchEngine.index(CONTACT_DOMAIN, contactFieldsB)).block();

            String response = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .withOptions(new Options(IGNORING_ARRAY_ORDER))
                .isEqualTo("[" +
                    "\"" + mailAddressA + "\"," +
                    "\"" + mailAddressB + "\"" +
                    "]");
        }
    }

    @Nested
    class GetAllContactsTest {
        @Test
        void getContactsShouldReturnEmptyByDefault() {
            List<String> contacts = given()
                .basePath(ALL_DOMAINS_PATH)
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".");

            assertThat(contacts).isEmpty();
        }

        @Test
        void getContactsShouldReturnListEntryWhenHasSingleElement() throws Exception {
            ContactFields contactFields = new ContactFields(new MailAddress(mailAddressA), firstnameA, surnameA);
            Mono.from(emailAddressContactSearchEngine.index(CONTACT_DOMAIN, contactFields)).block();

            String response = given()
                .basePath(ALL_DOMAINS_PATH)
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .isEqualTo("[" +
                    "\"" + mailAddressA + "\"" +
                    "]");
        }

        @Test
        void getContactsShouldReturnListEntryWhenHasMultipleElement() throws Exception {
            ContactFields contactFieldsA = new ContactFields(new MailAddress(mailAddressA), firstnameA, surnameA);
            Mono.from(emailAddressContactSearchEngine.index(CONTACT_DOMAIN, contactFieldsA)).block();

            ContactFields contactFieldsB = new ContactFields(new MailAddress(mailAddressB), firstnameB, surnameB);
            Mono.from(emailAddressContactSearchEngine.index(CONTACT_DOMAIN, contactFieldsB)).block();

            String response = given()
                .basePath(ALL_DOMAINS_PATH)
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .withOptions(new Options(IGNORING_ARRAY_ORDER))
                .isEqualTo("[" +
                    "\"" + mailAddressA + "\"," +
                    "\"" + mailAddressB + "\"" +
                    "]");
        }

        @Test
        void getContactsShouldReturnAllEntriesWhenMultipleDomains() throws Exception {
            ContactFields contactFieldsA = new ContactFields(new MailAddress(mailAddressA), firstnameA, surnameA);
            Mono.from(emailAddressContactSearchEngine.index(CONTACT_DOMAIN, contactFieldsA)).block();

            ContactFields contactFieldsB = new ContactFields(new MailAddress(mailAddressB), firstnameB, surnameB);
            Mono.from(emailAddressContactSearchEngine.index(CONTACT_DOMAIN, contactFieldsB)).block();

            ContactFields contactFieldsC = new ContactFields(new MailAddress("bob@other.com"), "Bob", "Other");
            Mono.from(emailAddressContactSearchEngine.index(Domain.of("other.com"), contactFieldsC)).block();

            String response = given()
                .basePath(ALL_DOMAINS_PATH)
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .withOptions(new Options(IGNORING_ARRAY_ORDER))
                .isEqualTo("[" +
                    "\"" + mailAddressA + "\"," +
                    "\"" + mailAddressB + "\"," +
                    "\"bob@other.com\"" +
                    "]");
        }
    }

    @Nested
    class CreateContactTest {
        @Test
        void createContactShouldStoreEntry() throws Exception {
            ContactFields contactFields = new ContactFields(new MailAddress(mailAddressA), firstnameA, surnameA);
            String request = "{" +
                "  \"emailAddress\": \"john@contact.com\"," +
                "  \"firstname\": \"John\"," +
                "  \"surname\": \"Carpenter\"" +
                "}";

            given()
                .body(request)
            .when()
                .post()
            .then()
                .statusCode(CREATED_201);

            assertThat(Flux.from(emailAddressContactSearchEngine.list(CONTACT_DOMAIN))
                    .map(contact -> contact.fields())
                    .collectList()
                    .block())
                .containsExactly(contactFields);
        }

        @Test
        void createContactShouldReturnIdAndLocationHeader() {
            String request = "{" +
                "  \"emailAddress\": \"john@contact.com\"," +
                "  \"firstname\": \"John\"," +
                "  \"surname\": \"Carpenter\"" +
                "}";

            String response = given()
                .body(request)
            .when()
                .post()
            .then()
                .statusCode(CREATED_201)
                .header(LOCATION.asString(), "/domains/contact.com/contacts/john")
                .extract()
                .body()
                .asString();

            assertThatJson(response).isEqualTo("{\"id\":\"${json-unit.ignore}\"}");
        }

        @Test
        void createContactShouldStoreEntryWithEmptyNames() throws Exception {
            ContactFields contactFields = new ContactFields(new MailAddress(mailAddressA), "", "");
            String request = "{" +
                "  \"emailAddress\": \"john@contact.com\"" +
                "}";

            given()
                .body(request)
            .when()
                .post()
            .then()
                .statusCode(CREATED_201);

            assertThat(Flux.from(emailAddressContactSearchEngine.list(CONTACT_DOMAIN))
                .map(contact -> contact.fields())
                .collectList()
                .block())
                .containsExactly(contactFields);
        }

        @Test
        void createContactShouldStoreEntryWithOnlyFirstname() throws Exception {
            ContactFields contactFields = new ContactFields(new MailAddress(mailAddressA), firstnameA, "");
            String request = "{" +
                "  \"emailAddress\": \"john@contact.com\"," +
                "  \"firstname\": \"John\"" +
                "}";

            given()
                .body(request)
            .when()
                .post()
            .then()
                .statusCode(CREATED_201);

            assertThat(Flux.from(emailAddressContactSearchEngine.list(CONTACT_DOMAIN))
                .map(contact -> contact.fields())
                .collectList()
                .block())
                .containsExactly(contactFields);
        }

        @Test
        void createContactShouldStoreEntryWithOnlySurname() throws Exception {
            ContactFields contactFields = new ContactFields(new MailAddress(mailAddressA), "", surnameA);
            String request = "{" +
                "  \"emailAddress\": \"john@contact.com\"," +
                "  \"surname\": \"Carpenter\"" +
                "}";

            given()
                .body(request)
            .when()
                .post()
            .then()
                .statusCode(CREATED_201);

            assertThat(Flux.from(emailAddressContactSearchEngine.list(CONTACT_DOMAIN))
                .map(contact -> contact.fields())
                .collectList()
                .block())
                .containsExactly(contactFields);
        }

        @Test
        void createContactShouldBeIdempotent() throws Exception {
            ContactFields contactFields = new ContactFields(new MailAddress(mailAddressA), firstnameA, surnameA);
            String request = "{" +
                "  \"emailAddress\": \"john@contact.com\"," +
                "  \"firstname\": \"John\"," +
                "  \"surname\": \"Carpenter\"" +
                "}";

            given()
                .body(request)
            .when()
                .post()
            .then()
                .statusCode(CREATED_201);

            given()
                .body(request)
            .when()
                .post()
            .then()
                .statusCode(CREATED_201);

            assertThat(Flux.from(emailAddressContactSearchEngine.list(CONTACT_DOMAIN))
                    .map(contact -> contact.fields())
                    .collectList()
                    .block())
                .containsExactly(contactFields);
        }

        @Test
        void createContactShouldThrowForDomainNotFound() {
            String request = "{" +
                "  \"emailAddress\": \"john@contact.com\"" +
                "}";

            Map<String, Object> errors = given()
                .basePath(String.format(DOMAINS_CONTACTS_PATH, "notfound.tld"))
                .body(request)
            .when()
                .post()
            .then()
                .statusCode(NOT_FOUND_404)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "The domain does not exist: notfound.tld");
        }

        @Test
        void createContactShouldThrowWhenDomainDifferentFromEmailDomain() {
            String request = "{" +
                "  \"emailAddress\": \"john@other.com\"" +
                "}";

            Map<String, Object> errors = given()
                .body(request)
            .when()
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "The domain contact.com does not match the one in the mail address: other.com");
        }

        @Test
        void createContactShouldThrowWhenEmailAddressIsWrong() {
            String request = "{" +
                "  \"emailAddress\": \"john@bob@contact.com\"" +
                "}";

            Map<String, Object> errors = given()
                .body(request)
            .when()
                .post()
            .then()
                .statusCode(BAD_REQUEST_400)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid character at 8 in 'john@bob@contact.com'");
        }
    }
}
