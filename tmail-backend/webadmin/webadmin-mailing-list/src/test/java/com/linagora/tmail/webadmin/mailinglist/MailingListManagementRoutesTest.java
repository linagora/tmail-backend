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
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.CONFLICT_409;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;

import java.util.List;
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
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

import io.restassured.RestAssured;

class MailingListManagementRoutesTest {
    private static final String BASE_DN = "ou=lists,dc=james,dc=org";
    private static final List<String> CREATED_LIST_LOCAL_PARTS = List.of("newlist", "twomembers", "onemember",
        "ownerlist", "sharedlocal");

    static LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;

    private WebAdminServer webAdminServer;
    private LDAPConnectionPool ldapConnectionPool;
    private LdapRepositoryConfiguration ldapConfiguration;

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
        ldapConfiguration = LdapRepositoryConfiguration.from(ldapRepositoryConfiguration(ldapContainer));
        ldapConnectionPool = new LDAPConnectionFactory(ldapConfiguration).getLdapConnectionPool();
        startServer(repository(false));
    }

    private MailingListRepository repository(boolean obmCompatibility) {
        MailingListConfiguration mailingListConfiguration = new MailingListConfiguration(
            Optional.of(BASE_DN), "description", obmCompatibility);
        return new LdapMailingListRepository(ldapConnectionPool, ldapConfiguration, mailingListConfiguration);
    }

    private void startServer(MailingListRepository repository) {
        JsonTransformer jsonTransformer = new JsonTransformer();
        webAdminServer = WebAdminUtils.createWebAdminServer(
                new MailingListRoutes(repository, jsonTransformer),
                new MailingListManagementRoutes(repository, jsonTransformer))
            .start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        CREATED_LIST_LOCAL_PARTS.forEach(this::deleteListEntryQuietly);
        webAdminServer.destroy();
        ldapConnectionPool.close();
    }

    private void deleteListEntryQuietly(String localPart) {
        try {
            SearchResult searchResult = ldapConnectionPool.search(BASE_DN, SearchScope.SUB,
                Filter.createEqualityFilter("cn", localPart));
            for (SearchResultEntry entry : searchResult.getSearchEntries()) {
                ldapConnectionPool.delete(entry.getDN());
            }
        } catch (LDAPException e) {
            if (!e.getResultCode().equals(ResultCode.NO_SUCH_OBJECT)) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void createShouldPersistTheMailingList() {
        given()
            .contentType(JSON)
            .body("""
                {
                  "businessCategory": "openList",
                  "members": ["james-user@james.org", "james-user2@james.org"],
                  "owners": ["james-user4@james.org"]
                }""")
            .put("/mailingLists/newlist@lists.james.org")
        .then()
            .statusCode(NO_CONTENT_204);

        String response = getList("newlist@lists.james.org");

        assertThatJson(response).inPath("mail").isEqualTo("newlist@lists.james.org");
        assertThatJson(response).inPath("businessCategory").isEqualTo("openList");
        assertThatJson(response).inPath("members").isArray()
            .containsExactlyInAnyOrder("james-user@james.org", "james-user2@james.org");
        assertThatJson(response).inPath("owners").isArray()
            .containsExactly("james-user4@james.org");
    }

    @Test
    void createShouldReturnConflictWhenListAlreadyExists() {
        given()
            .contentType(JSON)
            .body("""
                {"members": ["james-user@james.org"]}""")
            .put("/mailingLists/mygroup@lists.james.org")
        .then()
            .statusCode(CONFLICT_409);
    }

    @Test
    void createShouldAllowSameLocalPartInDifferentDomains() {
        createList("sharedlocal@lists.james.org", "james-user@james.org");

        given()
            .contentType(JSON)
            .body("""
                {"members": ["james-user@james.org"]}""")
            .put("/mailingLists/sharedlocal@other.james.org")
        .then()
            .statusCode(NO_CONTENT_204);
    }

    @Test
    void createObmListShouldReturnConflictForUnsupportedBusinessCategory() {
        webAdminServer.destroy();
        startServer(repository(true));

        given()
            .contentType(JSON)
            .body("""
                {"businessCategory": "internalList", "members": ["james-user@james.org"]}""")
            .put("/mailingLists/whatever@lists.james.org")
        .then()
            .statusCode(CONFLICT_409);
    }

    @Test
    void createShouldReturnBadRequestWhenNoMember() {
        given()
            .contentType(JSON)
            .body("""
                {"members": []}""")
            .put("/mailingLists/newlist@lists.james.org")
        .then()
            .statusCode(BAD_REQUEST_400);
    }

    @Test
    void createShouldReturnBadRequestWhenInvalidBusinessCategory() {
        given()
            .contentType(JSON)
            .body("""
                {"businessCategory": "invalid", "members": ["james-user@james.org"]}""")
            .put("/mailingLists/newlist@lists.james.org")
        .then()
            .statusCode(BAD_REQUEST_400);
    }

    @Test
    void createShouldReturnBadRequestWhenMemberIsNotAUser() {
        given()
            .contentType(JSON)
            .body("""
                {"members": ["not-a-user@james.org"]}""")
            .put("/mailingLists/newlist@lists.james.org")
        .then()
            .statusCode(BAD_REQUEST_400);
    }

    @Test
    void deleteShouldRemoveTheMailingList() {
        createList("newlist@lists.james.org", "james-user@james.org");

        given()
            .delete("/mailingLists/newlist@lists.james.org")
        .then()
            .statusCode(NO_CONTENT_204);

        given()
            .get("/mailingLists/newlist@lists.james.org")
        .then()
            .statusCode(NOT_FOUND_404);
    }

    @Test
    void deleteShouldReturnNotFoundWhenUnknownList() {
        given()
            .delete("/mailingLists/unknown@lists.james.org")
        .then()
            .statusCode(NOT_FOUND_404);
    }

    @Test
    void addMemberShouldAddTheMember() {
        createList("onemember@lists.james.org", "james-user@james.org");

        given()
            .put("/mailingLists/onemember@lists.james.org/members/james-user2@james.org")
        .then()
            .statusCode(NO_CONTENT_204);

        assertThatJson(getList("onemember@lists.james.org"))
            .inPath("members").isArray()
            .containsExactlyInAnyOrder("james-user@james.org", "james-user2@james.org");
    }

    @Test
    void addMemberShouldBeIdempotent() {
        createList("onemember@lists.james.org", "james-user@james.org");

        given().put("/mailingLists/onemember@lists.james.org/members/james-user2@james.org")
            .then().statusCode(NO_CONTENT_204);
        given().put("/mailingLists/onemember@lists.james.org/members/james-user2@james.org")
            .then().statusCode(NO_CONTENT_204);

        assertThatJson(getList("onemember@lists.james.org"))
            .inPath("members").isArray()
            .containsExactlyInAnyOrder("james-user@james.org", "james-user2@james.org");
    }

    @Test
    void addMemberShouldReturnNotFoundWhenUnknownList() {
        given()
            .put("/mailingLists/unknown@lists.james.org/members/james-user@james.org")
        .then()
            .statusCode(NOT_FOUND_404);
    }

    @Test
    void addMemberShouldReturnBadRequestWhenMemberIsNotAUser() {
        createList("onemember@lists.james.org", "james-user@james.org");

        given()
            .put("/mailingLists/onemember@lists.james.org/members/not-a-user@james.org")
        .then()
            .statusCode(BAD_REQUEST_400);
    }

    @Test
    void removeMemberShouldRemoveTheMember() {
        createList("twomembers@lists.james.org", "james-user@james.org", "james-user2@james.org");

        given()
            .delete("/mailingLists/twomembers@lists.james.org/members/james-user2@james.org")
        .then()
            .statusCode(NO_CONTENT_204);

        assertThatJson(getList("twomembers@lists.james.org"))
            .inPath("members").isArray()
            .containsExactly("james-user@james.org");
    }

    @Test
    void removeMemberShouldBeIdempotent() {
        createList("twomembers@lists.james.org", "james-user@james.org", "james-user2@james.org");

        given().delete("/mailingLists/twomembers@lists.james.org/members/james-user3@james.org")
            .then().statusCode(NO_CONTENT_204);
    }

    @Test
    void removeMemberShouldReturnConflictWhenRemovingLastMember() {
        createList("onemember@lists.james.org", "james-user@james.org");

        given()
            .delete("/mailingLists/onemember@lists.james.org/members/james-user@james.org")
        .then()
            .statusCode(CONFLICT_409);
    }

    @Test
    void addOwnerShouldAddTheOwner() {
        createList("ownerlist@lists.james.org", "james-user@james.org");

        given()
            .put("/mailingLists/ownerlist@lists.james.org/owners/james-user4@james.org")
        .then()
            .statusCode(NO_CONTENT_204);

        assertThatJson(getList("ownerlist@lists.james.org"))
            .inPath("owners").isArray()
            .containsExactly("james-user4@james.org");
    }

    @Test
    void removeOwnerShouldRemoveTheOwner() {
        createList("ownerlist@lists.james.org", "james-user@james.org");
        given().put("/mailingLists/ownerlist@lists.james.org/owners/james-user4@james.org")
            .then().statusCode(NO_CONTENT_204);

        given()
            .delete("/mailingLists/ownerlist@lists.james.org/owners/james-user4@james.org")
        .then()
            .statusCode(NO_CONTENT_204);

        assertThatJson(getList("ownerlist@lists.james.org"))
            .inPath("owners").isArray().isEmpty();
    }

    @Test
    void writesShouldReturnConflictWhenNotConfigured() {
        webAdminServer.destroy();
        startServer(new UnconfiguredMailingListRepository());

        given()
            .contentType(JSON)
            .body("""
                {"members": ["james-user@james.org"]}""")
            .put("/mailingLists/newlist@lists.james.org")
        .then()
            .statusCode(CONFLICT_409);

        given()
            .delete("/mailingLists/newlist@lists.james.org")
        .then()
            .statusCode(CONFLICT_409);
    }

    @Test
    void ownerWritesShouldReturnConflictForObmLists() {
        webAdminServer.destroy();
        startServer(repository(true));

        given()
            .put("/mailingLists/whatever@lists.james.org/owners/james-user@james.org")
        .then()
            .statusCode(CONFLICT_409);

        given()
            .delete("/mailingLists/whatever@lists.james.org/owners/james-user@james.org")
        .then()
            .statusCode(CONFLICT_409);
    }

    private void createList(String address, String... members) {
        StringBuilder memberArray = new StringBuilder();
        for (int i = 0; i < members.length; i++) {
            if (i > 0) {
                memberArray.append(", ");
            }
            memberArray.append('"').append(members[i]).append('"');
        }
        given()
            .contentType(JSON)
            .body("{\"members\": [" + memberArray + "]}")
            .put("/mailingLists/" + address)
        .then()
            .statusCode(NO_CONTENT_204);
    }

    private String getList(String address) {
        return given()
            .get("/mailingLists/" + address)
        .then()
            .statusCode(200)
            .extract().body().asString();
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
