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
import static org.hamcrest.Matchers.hasKey;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.jmap.event.ApplyWhenFilter;
import com.linagora.tmail.mailet.AIRedactionalHelper;
import com.linagora.tmail.mailet.AIRedactionalHelperForTest;
import com.linagora.tmail.mailet.conf.AIBaseModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.filter.SaaSPayingUser;
import com.linagora.tmail.saas.model.SaaSAccount;

import io.restassured.specification.RequestSpecification;
import reactor.core.publisher.Mono;

class AiBotCapabilitySaaSFilterIntegrationTest {

    static final String DOMAIN = "domain.tld";
    static final String PASSWORD = "secret";
    private static final Username PAYING_USER = Username.fromLocalPartWithDomain("paying", DOMAIN);
    private static final Username NON_PAYING_USER = Username.fromLocalPartWithDomain("nonpaying", DOMAIN);

    static final MemorySaaSAccountRepository SAAS_REPOSITORY = new MemorySaaSAccountRepository();

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
                .with(binder -> {
                    binder.bind(SaaSAccountRepository.class).toInstance(SAAS_REPOSITORY);
                    binder.bind(ApplyWhenFilter.class).toInstance(new SaaSPayingUser(SAAS_REPOSITORY));
                })))
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(PAYING_USER.asString(), PASSWORD)
            .addUser(NON_PAYING_USER.asString(), PASSWORD);

        Mono.from(SAAS_REPOSITORY.upsertSaasAccount(PAYING_USER, new SaaSAccount(true, true))).block();
        Mono.from(SAAS_REPOSITORY.upsertSaasAccount(NON_PAYING_USER, new SaaSAccount(true, false))).block();
    }

    @Test
    void shouldReturnAiBotCapabilityForPayingUser(GuiceJamesServer jamesServer) {
        RequestSpecification payingSpec = baseRequestSpecBuilder(jamesServer)
            .setAuth(authScheme(new UserCredential(PAYING_USER, PASSWORD)))
            .addHeader(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER())
            .build();

        given(payingSpec)
            .when()
            .get("/session")
        .then()
            .statusCode(SC_OK)
            .body("capabilities", hasKey("com:linagora:params:jmap:aibot"));
    }

    @Test
    void shouldNotReturnAiBotCapabilityForNonPayingUser(GuiceJamesServer jamesServer) {
        RequestSpecification nonPayingSpec = baseRequestSpecBuilder(jamesServer)
            .setAuth(authScheme(new UserCredential(NON_PAYING_USER, PASSWORD)))
            .addHeader(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER())
            .build();

        given(nonPayingSpec)
            .when()
            .get("/session")
        .then()
            .statusCode(SC_OK)
            .body("capabilities.'com:linagora:params:jmap:aibot'", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void shouldNotReturnAiBotCapabilityForUserWithNoSaaSAccount(GuiceJamesServer jamesServer) throws Exception {
        Username unknownUser = Username.fromLocalPartWithDomain("unknown", DOMAIN);
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addUser(unknownUser.asString(), PASSWORD);

        RequestSpecification unknownSpec = baseRequestSpecBuilder(jamesServer)
            .setAuth(authScheme(new UserCredential(unknownUser, PASSWORD)))
            .addHeader(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER())
            .build();

        given(unknownSpec)
            .when()
            .get("/session")
        .then()
            .statusCode(SC_OK)
            .body("capabilities.'com:linagora:params:jmap:aibot'", org.hamcrest.Matchers.nullValue());
    }
}
