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

import java.nio.charset.StandardCharsets;

import org.apache.james.util.Port;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

class TwakeCalendarGuiceServerTest  {
    @RegisterExtension
    static TwakeCalendarExtension twakeCalendarExtension = new TwakeCalendarExtension(TwakeCalendarConfiguration.builder()
        .configurationFromClasspath());

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
    }

    @Test
    void shouldExposeWebAdminHealthcheck() {
        String body = given()
        .when()
            .get("/healthcheck").prettyPeek()
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
    void shouldExposeMetrics() {
        String body = given()
        .when()
            .get("/metrics").prettyPeek()
        .then()
            .extract()
            .body()
            .asString();

        assertThat(body).contains("jvm_threads_runnable_count");
    }
}
