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
import com.linagora.calendar.storage.mongodb.DockerMongoDBExtension;
import com.linagora.calendar.storage.mongodb.MongoDBConfiguration;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

class MongoTest {
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
    DockerMongoDBExtension mongo = new DockerMongoDBExtension();
    @RegisterExtension
    TwakeCalendarExtension twakeCalendarExtension = new TwakeCalendarExtension(TwakeCalendarConfiguration.builder()
        .configurationFromClasspath()
        .userChoice(TwakeCalendarConfiguration.UserChoice.MEMORY)
        .dbChoice(TwakeCalendarConfiguration.DbChoice.MONGODB),
        binder -> binder.bind(URL.class).annotatedWith(Names.named("userInfo")).toProvider(MongoTest::getUserInfoTokenEndpoint),
        binder -> binder.bind(MongoDBConfiguration.class).toProvider(() -> mongo.getMongoDBConfiguration()));

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

    private static void targetRestAPI(TwakeCalendarGuiceServer server) {
        RestAssured.requestSpecification =  new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(server.getProbe(RestApiServerProbe.class).getPort().getValue())
            .setBasePath("/")
            .build();
    }
}
