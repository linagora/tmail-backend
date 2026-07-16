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

package com.linagora.tmail.jmap.aibot;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER;
import static org.apache.james.jmap.rfc8621.contract.Fixture.authScheme;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.util.Modules;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.mailet.AIRedactionalHelper;
import com.linagora.tmail.mailet.AIRedactionalHelperForTest;
import com.linagora.tmail.mailet.conf.AIBaseModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import io.restassured.specification.RequestSpecification;

class AiBotCapabilityFactoryIntegrationTest {

    static final String DOMAIN = "domain.tld";
    static final String PASSWORD = "secret";
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(Modules.override(new AIBaseModule())
                .with(binder -> binder.bind(AIRedactionalHelper.class).to(AIRedactionalHelperForTest.class))))
        .build();

    private RequestSpecification requestSpec;

    @BeforeEach
    void setUp(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), PASSWORD);

        requestSpec = baseRequestSpecBuilder(jamesServer)
            .setAuth(authScheme(new UserCredential(BOB, PASSWORD)))
            .addHeader(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER())
            .build();
    }

    @Test
    void shouldReturnAiBotCapabilityInSession(GuiceJamesServer jamesServer) {
        given(requestSpec)
            .when()
            .get("/session")
        .then()
            .statusCode(SC_OK)
            .body("capabilities", hasKey("com:linagora:params:jmap:aibot"));
    }

    @Test
    void shouldReturnCorrectScribeEndpointInCapability(GuiceJamesServer jamesServer) {
        given(requestSpec)
            .when()
            .get("/session")
        .then()
            .statusCode(SC_OK)
            .body("capabilities.'com:linagora:params:jmap:aibot'.scribeEndpoint",
                equalTo("http://localhost/ai/v1/chat/completions"));
    }

    @Test
    void shouldReturnAiBotCapabilityInAccountCapabilities(GuiceJamesServer jamesServer) {
        String body = given(requestSpec)
            .when()
            .get("/session")
        .then()
            .statusCode(SC_OK)
            .extract().body().asString();

        assertThat(body).contains("\"accountCapabilities\"");
        assertThat(body).contains("\"com:linagora:params:jmap:aibot\"");
    }
}
