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
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.util.Port;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.linagora.calendar.app.modules.CalendarDataProbe;
import com.linagora.calendar.restapi.RestApiServerProbe;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

class LdapTest {
    public static final Domain DOMAIN = Domain.of("james.org");
    private static final LdapGenericContainer ldapGenericContainer = DockerLdapSingleton.ldapContainer;

    static {
        ldapGenericContainer.start();
    }

    private static Module ldapModule() {
        return binder -> binder.bind(ConfigurationProvider.class).toProvider(() -> (s, l) -> new BaseHierarchicalConfiguration(baseConfiguration(ldapGenericContainer.getLdapHost())));
    }

    private static HierarchicalConfiguration<ImmutableNode> baseConfiguration(String ldapHost) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapHost);
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", "mysecretpassword");
        configuration.addProperty("[@userBase]", "ou=People,dc=james,dc=org");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");

        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("enableVirtualHosting", true);
        return configuration;
    }

    @RegisterExtension
    static TwakeCalendarExtension twakeCalendarExtension = new TwakeCalendarExtension(
        TwakeCalendarConfiguration.builder()
            .configurationFromClasspath()
            .userChoice(TwakeCalendarConfiguration.UserChoice.LDAP)
            .dbChoice(TwakeCalendarConfiguration.DbChoice.MEMORY),
        ldapModule());

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

        server.getProbe(CalendarDataProbe.class).addDomain(DOMAIN);
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

        assertThatJson(body).withOptions(IGNORING_ARRAY_ORDER).isEqualTo("""
            {
                "status": "healthy",
                "checks": [
                    {
                        "componentName":"LDAP User Server",
                        "escapedComponentName":"LDAP%20User%20Server",
                        "status":"healthy",
                        "cause":null
                    },
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
    void shouldExposeWebAdminUsers() {
        String body = given()
        .when()
            .get("/users")
        .then()
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
            [{"username":"james-user@james.org"}]""");
    }
}
