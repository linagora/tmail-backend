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
package com.linagora.tmail.agent;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.requestSpecification;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.rfc8621.contract.Fixture.authScheme;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;
import static org.hamcrest.Matchers.equalTo;

import com.google.inject.util.Modules;
import com.linagora.tmail.common.AIBaseModule;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.jmap.event.ApplyWhenFilter;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.filter.SaaSPayingUser;
import com.linagora.tmail.saas.model.SaaSAccount;
import io.restassured.specification.RequestSpecification;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Mono;

public class AiToolsRoutesTest {

    private static final String AI_TOOLS_ENDPOINT = "/ai/v1/tools";
    static final String DOMAIN = "domain.tld";
    static final String PASSWORD = "secret";
    private static final Username PAYING_USER = Username.fromLocalPartWithDomain("paying", DOMAIN);
    private static final Username NON_PAYING_USER = Username.fromLocalPartWithDomain("nonpaying", DOMAIN);
    private static final Username USER_WITH_NO_SAAS = Username.fromLocalPartWithDomain("no-saas", DOMAIN);
    private static final MemorySaaSAccountRepository SAAS_REPOSITORY = new MemorySaaSAccountRepository();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension =  new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(Modules.override(new AIBaseModule())
                .with(binder -> {
                    binder.bind(ApplyWhenFilter.class).toInstance(new SaaSPayingUser(SAAS_REPOSITORY));
                    binder.bind(SaaSAccountRepository.class).toInstance(SAAS_REPOSITORY);
                })))
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(PAYING_USER.asString(), PASSWORD)
            .addUser(NON_PAYING_USER.asString(), PASSWORD)
            .addUser(USER_WITH_NO_SAAS.asString(), PASSWORD);

        Mono.from(SAAS_REPOSITORY.upsertSaasAccount(PAYING_USER, new SaaSAccount(true, true))).block();
        Mono.from(SAAS_REPOSITORY.upsertSaasAccount(NON_PAYING_USER, new SaaSAccount(true, false))).block();

        requestSpecification = baseRequestSpecBuilder(jamesServer)
            .setBasePath(AI_TOOLS_ENDPOINT)
            .setAuth(authScheme(new UserCredential(PAYING_USER, PASSWORD)))
            .build();
    }

    @Test
    void shouldReturn200WhenAccessingAiToolsEndpoint() {

        String resultBody = """
        {
                "tools": [
            {
                "type": "function",
                "function": {
                "name": "create_draft",
                    "description": "Create an email draft in the user's Drafts folder. The draft is not sent; the user will review and send it. Use inReplyTo to draft a reply to an existing email.",
                    "parameters": {
                    "type": "object",
                        "properties": {
                        "to": {
                            "type": "array",
                                "items": { "type": "string" },
                            "description": "Recipient email addresses"
                        },
                        "cc": {
                            "type": "array",
                                "items": { "type": "string" },
                            "description": "CC email addresses"
                        },
                        "subject": {
                            "type": "string",
                                "description": "Email subject"
                        },
                        "body": {
                            "type": "string",
                                "description": "Plain text body of the email"
                        },
                        "inReplyTo": {
                            "type": "string",
                                "description": "Id of the email being replied to, as returned by search_emails"
                        }
                    },
                    "required": ["body"]
                }
            }
            },
            {
                "type": "function",
                "function": {
                "name": "search_emails",
                    "description": "Search the user's emails. Returns a list of matching emails with id, sender, subject, date and a short preview. Use the returned id with create_draft to reply to an email.",
                    "parameters": {
                    "type": "object",
                        "properties": {
                        "from": {
                            "type": "string",
                                "description": "Sender email address"
                        },
                        "keyword": {
                            "type": "string",
                                "description": "Text to search in subject and body"
                        },
                        "unread": {
                            "type": "boolean",
                                "description": "Only return unread emails"
                        },
                        "after": {
                            "type": "string",
                                "description": "Only emails received after this date (ISO 8601, e.g. 2026-10-14)"
                        },
                        "limit": {
                            "type": "integer",
                                "description": "Maximum number of results, default 10, max 20"
                        }
                    },
                    "required": []
                }
            }
            }
      ]
    }""";

        
        String response = given()
            .when()
            .get()
            .then()
            .statusCode(SC_OK)
            .header("Access-Control-Allow-Origin", "*")
            .contentType("application/json")
            .extract()
            .body()
            .jsonPath()
            .prettify();

        assertThatJson(response).isEqualTo(resultBody);
    }

    @Test
    void aiToolsRouteShouldReturnErrorWhenWrongAuth(GuiceJamesServer jamesServer) {
        RequestSpecification unauthorizedSpecification = baseRequestSpecBuilder(jamesServer)
            .setBasePath(AI_TOOLS_ENDPOINT)
            .setAuth(authScheme(new UserCredential(Username.fromLocalPartWithDomain("unknown", DOMAIN), PASSWORD)))
            .build();

        given(unauthorizedSpecification)
        .when()
            .get()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .contentType("application/json");
    }

    @Test
    void aiToolsRouteShouldRejectUserWithNoSaasAccount(GuiceJamesServer jamesServer) {
        RequestSpecification noSaasSpecification = baseRequestSpecBuilder(jamesServer)
            .setBasePath(AI_TOOLS_ENDPOINT)
            .setAuth(authScheme(new UserCredential(USER_WITH_NO_SAAS, PASSWORD)))
            .build();

        given(noSaasSpecification)
        .when()
            .get()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .contentType("application/json");
    }

    @Test
    void aiToolsRouteShouldRejectNonPayingUser(GuiceJamesServer jamesServer) {
        RequestSpecification nonPayingSpecification = baseRequestSpecBuilder(jamesServer)
            .setBasePath(AI_TOOLS_ENDPOINT)
            .setAuth(authScheme(new UserCredential(NON_PAYING_USER, PASSWORD)))
            .build();

        given(nonPayingSpecification)
        .when()
            .get()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .contentType("application/json")
            .body("detail", equalTo("User is not eligible for AI tools"));
    }
}
