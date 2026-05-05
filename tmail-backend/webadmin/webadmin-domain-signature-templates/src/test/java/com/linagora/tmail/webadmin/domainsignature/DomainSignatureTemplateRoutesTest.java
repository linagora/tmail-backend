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
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.event.DomainSignatureTemplate;
import com.linagora.tmail.james.jmap.event.MemoryDomainSignatureTemplateRepository;
import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;

import io.restassured.RestAssured;

class DomainSignatureTemplateRoutesTest {

    private static final Domain DOMAIN = Domain.of("linagora.com");
    private static final String DOMAIN_PATH = "/domains/linagora.com/signature-templates";

    private WebAdminServer webAdminServer;
    private MemoryDomainSignatureTemplateRepository repository;
    private DomainList domainList;

    @BeforeEach
    void setUp() throws Exception {
        repository = new MemoryDomainSignatureTemplateRepository();
        domainList = mock(DomainList.class);
        when(domainList.containsDomain(DOMAIN)).thenReturn(true);

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
        void getShouldReturn404WhenDomainNotFound() throws Exception {
            when(domainList.containsDomain(Domain.of("unknown.com"))).thenReturn(false);

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
        void putShouldReturn404WhenDomainNotFound() throws Exception {
            when(domainList.containsDomain(Domain.of("unknown.com"))).thenReturn(false);

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
        void deleteShouldReturn404WhenDomainNotFound() throws Exception {
            when(domainList.containsDomain(Domain.of("unknown.com"))).thenReturn(false);

            when()
                .delete("/domains/unknown.com/signature-templates")
            .then()
                .statusCode(NOT_FOUND_404);
        }
    }
}
