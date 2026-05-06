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
 *******************************************************************/

package com.linagora.tmail.webadmin.domainsignature;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NOT_IMPLEMENTED_501;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.jmap.api.identity.DefaultIdentitySupplier;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.memory.identity.MemoryCustomIdentityDAO;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.user.ldap.ReadOnlyUsersLDAPRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.domainsignature.DomainSignatureTemplateApplyService;
import com.linagora.tmail.james.jmap.event.DomainBasedSignatureTextFactory;
import com.linagora.tmail.james.jmap.event.DomainSignatureTemplate;
import com.linagora.tmail.james.jmap.event.IdentityCreationRequestBuilder;
import com.linagora.tmail.james.jmap.event.MemoryDomainSignatureTemplateRepository;
import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;
import com.unboundid.ldap.sdk.LDAPConnectionPool;

import io.restassured.RestAssured;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DomainSignatureTemplateRoutesTest {

    private static final Domain DOMAIN = Domain.of("linagora.com");
    private static final String DOMAIN_PATH = "/domains/linagora.com/signature-templates";

    private WebAdminServer webAdminServer;
    private MemoryDomainSignatureTemplateRepository repository;
    private SimpleDomainList domainList;

    @BeforeEach
    void setUp() throws Exception {
        repository = new MemoryDomainSignatureTemplateRepository();
        domainList = new SimpleDomainList();
        domainList.addDomain(DOMAIN);

        DomainSignatureTemplateRoutes routes = new DomainSignatureTemplateRoutes(
            repository, domainList, new JsonTransformer(), Optional.empty());
        webAdminServer = WebAdminUtils.createWebAdminServer(routes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class GetTemplate {
        @Test
        void getShouldReturn404WhenNoTemplate() {
            when()
                .get(DOMAIN_PATH)
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void getShouldReturn404WhenDomainNotFound() {
            when()
                .get("/domains/unknown.com/signature-templates")
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void getShouldReturnStoredTemplate() {
            repository.store(DOMAIN, new DomainSignatureTemplate(Map.of(
                Locale.ENGLISH, new SignatureText("en text", "<p>en</p>"),
                Locale.FRENCH, new SignatureText("fr text", "<p>fr</p>")))).block();

            String body = when()
                .get(DOMAIN_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(body).node("signatures").isArray().hasSize(2);
        }

        @Test
        void getShouldReturnAllFields() {
            repository.store(DOMAIN, new DomainSignatureTemplate(Map.of(
                Locale.ENGLISH, new SignatureText("en text", "<p>en html</p>")))).block();

            String body = when()
                .get(DOMAIN_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(body).node("signatures[0]")
                .isObject()
                .containsKey("language")
                .containsKey("textSignature")
                .containsKey("htmlSignature");
        }
    }

    @Nested
    class PutTemplate {
        @Test
        void putShouldReturn204OnSuccess() {
            given()
                .contentType(JSON)
                .body("""
                    {
                        "signatures": [
                            {"language": "en", "textSignature": "Best regards", "htmlSignature": "<p>Best regards</p>"}
                        ]
                    }""")
            .when()
                .put(DOMAIN_PATH)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void putShouldStoreTemplate() {
            given()
                .contentType(JSON)
                .body("""
                    {
                        "signatures": [
                            {"language": "en", "textSignature": "Best regards", "htmlSignature": "<p>Best regards</p>"}
                        ]
                    }""")
            .when()
                .put(DOMAIN_PATH);

            String body = when()
                .get(DOMAIN_PATH)
            .then()
                .statusCode(OK_200)
                .extract().body().asString();

            assertThatJson(body).node("signatures[0].textSignature").isEqualTo("\"Best regards\"");
        }

        @Test
        void putShouldReturn400WhenEmptySignatures() {
            given()
                .contentType(JSON)
                .body("""
                    {"signatures": []}""")
            .when()
                .put(DOMAIN_PATH)
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void putShouldReturn404WhenDomainNotFound() {
            given()
                .contentType(JSON)
                .body("""
                    {
                        "signatures": [
                            {"language": "en", "textSignature": "t", "htmlSignature": "h"}
                        ]
                    }""")
            .when()
                .put("/domains/unknown.com/signature-templates")
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void putShouldSupportMultipleLocales() {
            given()
                .contentType(JSON)
                .body("""
                    {
                        "signatures": [
                            {"language": "en", "textSignature": "Best regards", "htmlSignature": "<p>BR</p>"},
                            {"language": "fr", "textSignature": "Cordialement", "htmlSignature": "<p>Cordialement</p>"}
                        ]
                    }""")
            .when()
                .put(DOMAIN_PATH)
            .then()
                .statusCode(NO_CONTENT_204);
        }
    }

    @Nested
    class DeleteTemplate {
        @Test
        void deleteShouldReturn204WhenExists() {
            repository.store(DOMAIN, new DomainSignatureTemplate(Map.of(
                Locale.ENGLISH, new SignatureText("t", "h")))).block();

            when()
                .delete(DOMAIN_PATH)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldReturn204WhenNotExists() {
            when()
                .delete(DOMAIN_PATH)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteShouldRemoveTemplate() {
            repository.store(DOMAIN, new DomainSignatureTemplate(Map.of(
                Locale.ENGLISH, new SignatureText("t", "h")))).block();

            when().delete(DOMAIN_PATH);

            when()
                .get(DOMAIN_PATH)
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void deleteShouldReturn404WhenDomainNotFound() {
            when()
                .delete("/domains/unknown.com/signature-templates")
            .then()
                .statusCode(NOT_FOUND_404);
        }
    }

    @Nested
    class PostApply {

        private static final String LDAP_ADMIN_PASSWORD = "mysecretpassword";
        private static final Domain LDAP_DOMAIN = Domain.of("domain.tld");
        private static final String LDAP_DOMAIN_PATH = "/domains/domain.tld/signature-templates";

        static LdapGenericContainer ldapContainer = LdapGenericContainer.builder()
            .domain("domain.tld")
            .password(LDAP_ADMIN_PASSWORD)
            .dockerFilePrefix("domain-signature-ldap/")
            .build();

        private MemoryDomainSignatureTemplateRepository ldapRepository;
        private SimpleDomainList ldapDomainList;
        private IdentityRepository identityRepository;

        @BeforeAll
        static void setUpLdap() {
            ldapContainer.start();
        }

        @AfterAll
        static void tearDownLdap() {
            ldapContainer.stop();
        }

        @BeforeEach
        void setUp() throws Exception {
            webAdminServer.destroy();

            HierarchicalConfiguration<ImmutableNode> ldapConfig = ldapConfig();
            LdapRepositoryConfiguration ldapRepositoryConfiguration = LdapRepositoryConfiguration.from(ldapConfig);
            LDAPConnectionPool ldapConnectionPool = new LDAPConnectionFactory(ldapRepositoryConfiguration).getLdapConnectionPool();

            ReadOnlyUsersLDAPRepository usersRepository = new ReadOnlyUsersLDAPRepository(
                new SimpleDomainList(), new NoopGaugeRegistry(), ldapConnectionPool, ldapRepositoryConfiguration);
            usersRepository.configure(ldapConfig);
            usersRepository.init();

            ldapRepository = new MemoryDomainSignatureTemplateRepository();
            MemoryJmapSettingsRepository jmapSettingsRepo = new MemoryJmapSettingsRepository();
            DomainBasedSignatureTextFactory signatureFactory = new DomainBasedSignatureTextFactory(ldapRepository, jmapSettingsRepo);

            InVMEventBus eventBus = new InVMEventBus(
                new InVmEventDelivery(new RecordingMetricFactory()),
                RetryBackoffConfiguration.FAST,
                new MemoryEventDeadLetters());
            MemoryCustomIdentityDAO identityDAO = new MemoryCustomIdentityDAO(eventBus);

            SimpleDomainList identityDomainList = new SimpleDomainList();
            identityDomainList.addDomain(LDAP_DOMAIN);
            MemoryRecipientRewriteTable rrt = new MemoryRecipientRewriteTable();
            rrt.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
            CanSendFromImpl canSendFrom = new CanSendFromImpl(new AliasReverseResolverImpl(rrt));
            MemoryUsersRepository usersRepoForIdentity = MemoryUsersRepository.withVirtualHosting(identityDomainList);
            DefaultIdentitySupplier defaultIdentitySupplier = new DefaultIdentitySupplier(canSendFrom, usersRepoForIdentity);
            identityRepository = new IdentityRepository(identityDAO, defaultIdentitySupplier);

            DomainSignatureTemplateApplyService applyService = new DomainSignatureTemplateApplyService(
                ldapRepository, usersRepository, signatureFactory,
                identityRepository, ldapConnectionPool, ldapRepositoryConfiguration);

            ldapDomainList = new SimpleDomainList();
            ldapDomainList.addDomain(LDAP_DOMAIN);

            repository = ldapRepository;
            DomainSignatureTemplateRoutes routes = new DomainSignatureTemplateRoutes(
                ldapRepository, ldapDomainList, new JsonTransformer(), Optional.of(applyService));
            webAdminServer = WebAdminUtils.createWebAdminServer(routes).start();
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        }

        @Test
        void postShouldReturn501WhenServiceNotConfigured() {
            webAdminServer.destroy();
            DomainSignatureTemplateRoutes routes = new DomainSignatureTemplateRoutes(
                ldapRepository, ldapDomainList, new JsonTransformer(), Optional.empty());
            webAdminServer = WebAdminUtils.createWebAdminServer(routes).start();
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();

            given()
                .queryParam("action", "apply")
            .when()
                .post(LDAP_DOMAIN_PATH)
            .then()
                .statusCode(NOT_IMPLEMENTED_501);
        }

        @Test
        void postShouldReturn400WhenUnknownAction() {
            given()
                .queryParam("action", "unknown")
            .when()
                .post(LDAP_DOMAIN_PATH)
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void postShouldReturn400WhenNoActionParam() {
            when()
                .post(LDAP_DOMAIN_PATH)
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void postShouldReturn404WhenDomainNotFound() {
            when()
                .post("/domains/unknown.com/signature-templates?action=apply")
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void postShouldReturn404WhenNoTemplateStored() {
            given()
                .queryParam("action", "apply")
            .when()
                .post(LDAP_DOMAIN_PATH)
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void postShouldReturn200WhenTemplateExists() {
            ldapRepository.store(LDAP_DOMAIN, new DomainSignatureTemplate(Map.of(
                Locale.ENGLISH, new SignatureText("Best regards", "<p>Best regards</p>")))).block();

            String body = given()
                .queryParam("action", "apply")
            .when()
                .post(LDAP_DOMAIN_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(body)
                .isObject()
                .containsKey("applied")
                .containsKey("skipped")
                .containsKey("error");
        }

        @Test
        void postShouldReturnSkippedForAllUsersWhenNoIdentityStored() {
            ldapRepository.store(LDAP_DOMAIN, new DomainSignatureTemplate(Map.of(
                Locale.ENGLISH, new SignatureText("Best regards", "<p>Best regards</p>")))).block();

            String body = given()
                .queryParam("action", "apply")
            .when()
                .post(LDAP_DOMAIN_PATH)
            .then()
                .statusCode(OK_200)
                .extract().body().asString();

            assertThatJson(body).node("applied").isEqualTo(0);
            assertThatJson(body).node("skipped").isEqualTo(2);
            assertThatJson(body).node("error").isEqualTo(0);
        }

        @Test
        void postShouldReturnAppliedForUsersWithEmptyDefaultIdentity() throws Exception {
            ldapRepository.store(LDAP_DOMAIN, new DomainSignatureTemplate(Map.of(
                Locale.ENGLISH, new SignatureText("Best regards", "<p>Best regards</p>")))).block();
            saveDefaultIdentity("bob@domain.tld", "", "");
            saveDefaultIdentity("alice@domain.tld", "", "");

            String body = given()
                .queryParam("action", "apply")
            .when()
                .post(LDAP_DOMAIN_PATH)
            .then()
                .statusCode(OK_200)
                .extract().body().asString();

            assertThatJson(body).node("applied").isEqualTo(2);
            assertThatJson(body).node("skipped").isEqualTo(0);
            assertThatJson(body).node("error").isEqualTo(0);
        }

        @Test
        void postShouldSkipUsersWithExistingSignatures() throws Exception {
            ldapRepository.store(LDAP_DOMAIN, new DomainSignatureTemplate(Map.of(
                Locale.ENGLISH, new SignatureText("Best regards", "<p>Best regards</p>")))).block();
            saveDefaultIdentity("bob@domain.tld", "existing text", "<p>existing</p>");
            saveDefaultIdentity("alice@domain.tld", "", "");

            String body = given()
                .queryParam("action", "apply")
            .when()
                .post(LDAP_DOMAIN_PATH)
            .then()
                .statusCode(OK_200)
                .extract().body().asString();

            assertThatJson(body).node("applied").isEqualTo(1);
            assertThatJson(body).node("skipped").isEqualTo(1);
            assertThatJson(body).node("error").isEqualTo(0);
        }

        @Test
        void postShouldInterpolateLdapAttributesFromRealDirectory() throws Exception {
            ldapRepository.store(LDAP_DOMAIN, new DomainSignatureTemplate(Map.of(
                Locale.ENGLISH, new SignatureText(
                    "Regards, {ldap:givenName} {ldap:sn}",
                    "<p>Regards, {ldap:givenName} {ldap:sn}</p>")))).block();
            saveDefaultIdentity("bob@domain.tld", "", "");
            saveDefaultIdentity("alice@domain.tld", "", "");

            String body = given()
                .queryParam("action", "apply")
            .when()
                .post(LDAP_DOMAIN_PATH)
            .then()
                .statusCode(OK_200)
                .extract().body().asString();

            assertThatJson(body).node("applied").isEqualTo(2);
            assertThatJson(body).node("error").isEqualTo(0);
        }

        private static HierarchicalConfiguration<ImmutableNode> ldapConfig() {
            PropertyListConfiguration configuration = new PropertyListConfiguration();
            configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
            configuration.addProperty("[@principal]", "cn=admin,dc=domain,dc=tld");
            configuration.addProperty("[@credentials]", LDAP_ADMIN_PASSWORD);
            configuration.addProperty("[@userBase]", "ou=people,dc=domain,dc=tld");
            configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
            configuration.addProperty("[@userIdAttribute]", "mail");
            configuration.addProperty("[@connectionTimeout]", "2000");
            configuration.addProperty("[@readTimeout]", "2000");
            configuration.addProperty("supportsVirtualHosting", true);
            return configuration;
        }

        private void saveDefaultIdentity(String email, String text, String html) throws Exception {
            Username user = Username.of(email);
            Mono.from(identityRepository.save(user, IdentityCreationRequestBuilder.builder()
                .email(user.asMailAddress())
                .name(email)
                .sortOrder(0)
                .textSignature(text)
                .htmlSignature(html)
                .build())).block();
        }
    }

    @Nested
    class PostApplyResponseBody {

        @Test
        void postShouldReturn501WhenServiceNotConfigured() {
            given()
                .queryParam("action", "apply")
            .when()
                .post(DOMAIN_PATH)
            .then()
                .statusCode(NOT_IMPLEMENTED_501);
        }
    }
}
