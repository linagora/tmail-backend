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
package com.linagora.tmail.james.app;

import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.mailbox.model.MailboxPath;

import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.rfc8621.contract.Fixture.*;
import static org.hamcrest.CoreMatchers.equalTo;

public class ExtentionMecanismTest {

    static final String DOMAIN = "james.org";
    static final String PASSWORD = "secret";

    private static final String BOB = "bob@" + DOMAIN;
    private static final String ALICE = "alice@" + DOMAIN;

    private String accountId;
    private MailboxPath path;

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .mailbox(new MailboxConfiguration(false))
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule()))
        .build();

        @BeforeEach
        void setUp(GuiceJamesServer jamesServer) throws Exception {
            jamesServer.getProbe(DataProbeImpl.class).fluent()
                .addDomain(DOMAIN)
                .addUser(ALICE, PASSWORD);
            path = MailboxPath.inbox(Username.of(ALICE));
            jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(path);
            accountId = getAccountId(Username.of(ALICE),PASSWORD,jamesServer);
        }

    @Test
    public void connectPOP3ServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) {
        Assertions.assertTrue(jamesServer.isStarted());
    }
    @Test
    public void shoudSuggestContentWhenNoEmailId(GuiceJamesServer jamesServer) throws Exception {
        String request = String.format("{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\": \"%s\"," +
            "      \"userInput\": \"explain to him how to cook an egg\"" +
            "    }, \"0\"]" +
            "  ]" +
            "}", accountId);

        given(buildJmapRequestSpecification(Username.of(ALICE), PASSWORD, jamesServer))
            .body(String.format(request))
            .when()
            .post()
            .then()
            .statusCode(SC_OK)
            .body("methodResponses[0][0]", equalTo("AiBot/Suggest"))
            .body("methodResponses[0][1].accountId", equalTo(accountId))
            .body("methodResponses[0][1].suggestion", equalTo("This suggestion is just for testing purpose this is your UserInput: explain to him how to cook an egg This is you mailContent: "));
    }
    private RequestSpecification buildJmapRequestSpecification(Username username, String password, GuiceJamesServer jamesServer) {
        return baseRequestSpecBuilder(jamesServer)
            .setAuth(authScheme(new UserCredential(username, password)))
            .addHeader(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER())
            .build();
    }

    public static String getAccountId(Username username, String password, GuiceJamesServer server) {
        Response response = given(baseRequestSpecBuilder(server)
            .setAuth(authScheme(new UserCredential(username, password)))
            .addHeader(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER())
            .build())
            .get("/session")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .extract().response();
        return response.jsonPath().getString("primaryAccounts[\"urn:ietf:params:jmap:core\"]");
    }

}
