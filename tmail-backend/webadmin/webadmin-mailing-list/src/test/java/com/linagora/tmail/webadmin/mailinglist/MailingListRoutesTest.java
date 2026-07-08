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

package com.linagora.tmail.webadmin.mailinglist;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.eclipse.jetty.http.HttpStatus.CONFLICT_409;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.mailet.MailingListConfiguration;
import com.unboundid.ldap.sdk.LDAPConnectionPool;

import io.restassured.RestAssured;

class MailingListRoutesTest {
    private static final String BASE_DN = "ou=lists,dc=james,dc=org";
    static LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;

    private WebAdminServer webAdminServer;
    private LDAPConnectionPool ldapConnectionPool;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        LdapRepositoryConfiguration ldapConfiguration = LdapRepositoryConfiguration.from(
            ldapRepositoryConfiguration(ldapContainer));
        ldapConnectionPool = new LDAPConnectionFactory(ldapConfiguration).getLdapConnectionPool();
        MailingListConfiguration mailingListConfiguration = new MailingListConfiguration(
            Optional.of(BASE_DN), "description", false);
        MailingListRepository repository = new LdapMailingListRepository(ldapConnectionPool, ldapConfiguration, mailingListConfiguration);

        startServer(repository);
    }

    private void startServer(MailingListRepository repository) {
        webAdminServer = WebAdminUtils.createWebAdminServer(new MailingListRoutes(repository, new JsonTransformer()))
            .start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        ldapConnectionPool.close();
    }

    @Test
    void listShouldReturnAllMailingLists() {
        String response = given()
            .get("/mailingLists")
        .then()
            .statusCode(OK_200)
            .contentType(JSON)
            .extract().body().asString();

        assertThatJson(response).isArray()
            .contains("mygroup@lists.james.org", "group2@lists.james.org", "group7@localhost");
    }

    @Test
    void listShouldFilterByDomain() {
        String response = given()
            .queryParam("domain", "localhost")
            .get("/mailingLists")
        .then()
            .statusCode(OK_200)
            .contentType(JSON)
            .extract().body().asString();

        assertThatJson(response).isArray()
            .containsExactly("group7@localhost");
    }

    @Test
    void listShouldOmitOtherDomains() {
        String response = given()
            .queryParam("domain", "lists.james.org")
            .get("/mailingLists")
        .then()
            .statusCode(OK_200)
            .contentType(JSON)
            .extract().body().asString();

        assertThatJson(response).isArray()
            .contains("mygroup@lists.james.org")
            .doesNotContain("group7@localhost");
    }

    @Test
    void getShouldReturnMailingListDetails() {
        String response = given()
            .get("/mailingLists/mygroup@lists.james.org")
        .then()
            .statusCode(OK_200)
            .contentType(JSON)
            .extract().body().asString();

        assertThatJson(response).isEqualTo("""
            {
              "mail": "mygroup@lists.james.org",
              "businessCategory": "",
              "members": ["james-user@james.org", "james-user2@james.org"],
              "owners": []
            }""");
    }

    @Test
    void getShouldReturnOwnersAndBusinessCategory() {
        String response = given()
            .get("/mailingLists/group5@lists.james.org")
        .then()
            .statusCode(OK_200)
            .contentType(JSON)
            .extract().body().asString();

        assertThatJson(response)
            .inPath("businessCategory").isEqualTo("ownerRestrictedList");
        assertThatJson(response)
            .inPath("owners").isArray()
            .containsExactlyInAnyOrder("james-user4@james.org", "james-user2@james.org");
    }

    @Test
    void getShouldExpandNestedGroupMembers() {
        String response = given()
            .get("/mailingLists/nested@lists.james.org")
        .then()
            .statusCode(OK_200)
            .contentType(JSON)
            .extract().body().asString();

        assertThatJson(response)
            .inPath("members").isArray()
            .containsExactlyInAnyOrder("james-user@james.org", "james-user2@james.org", "james-user5@james.org");
    }

    @Test
    void getShouldExpandNestedGroupOwners() {
        String response = given()
            .get("/mailingLists/nestedOwner@lists.james.org")
        .then()
            .statusCode(OK_200)
            .contentType(JSON)
            .extract().body().asString();

        assertThatJson(response)
            .inPath("owners").isArray()
            .containsExactlyInAnyOrder("james-user@james.org", "james-user2@james.org");
        assertThatJson(response)
            .inPath("members").isArray()
            .containsExactlyInAnyOrder("james-user3@james.org", "james-user5@james.org");
    }

    @Test
    void getShouldNotLoopWhenNestedGroupsReferenceEachOther() {
        String response = given()
            .get("/mailingLists/loop1@lists.james.org")
        .then()
            .statusCode(OK_200)
            .contentType(JSON)
            .extract().body().asString();

        assertThatJson(response)
            .inPath("members").isArray().isEmpty();
    }

    @Test
    void getShouldReturnNotFoundWhenUnknownList() {
        given()
            .get("/mailingLists/unknown@lists.james.org")
        .then()
            .statusCode(NOT_FOUND_404);
    }

    @Test
    void routesShouldReturnConflictWhenNotConfigured() {
        webAdminServer.destroy();
        startServer(new UnconfiguredMailingListRepository());

        given()
            .get("/mailingLists")
        .then()
            .statusCode(CONFLICT_409);

        given()
            .get("/mailingLists/mygroup@lists.james.org")
        .then()
            .statusCode(CONFLICT_409);
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=people,dc=james,dc=org");
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@connectionTimeout]", "2000");
        configuration.addProperty("[@readTimeout]", "2000");
        configuration.addProperty("supportsVirtualHosting", true);
        configuration.addProperty("[@administratorId]", ADMIN.asString());
        return configuration;
    }
}
