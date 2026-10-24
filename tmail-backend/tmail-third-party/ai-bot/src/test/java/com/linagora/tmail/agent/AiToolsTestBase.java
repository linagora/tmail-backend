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
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.rfc8621.contract.Fixture.authScheme;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.google.inject.Module;
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
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.mail.Flags;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import reactor.core.publisher.Mono;

abstract class AiToolsTestBase {

    static final String AI_TOOLS_ENDPOINT = "/ai/v1/tools";
    static final String DOMAIN = "domain.tld";
    static final String PASSWORD = "secret";
    static final Username PAYING_USER = Username.fromLocalPartWithDomain("paying", DOMAIN);
    static final Username NON_PAYING_USER = Username.fromLocalPartWithDomain("nonpaying", DOMAIN);
    static final Username USER_WITH_NO_SAAS = Username.fromLocalPartWithDomain("no-saas", DOMAIN);
    static final MailboxPath INBOX = MailboxPath.inbox(PAYING_USER);
    static final MailboxPath ARCHIVE = MailboxPath.forUser(PAYING_USER, "Archive");
    static final MailboxPath DRAFTS = MailboxPath.forUser(PAYING_USER, "Drafts");
    static final MemorySaaSAccountRepository SAAS_REPOSITORY = new MemorySaaSAccountRepository();

    static JamesServerExtension server(Module... overrides) {
        return new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
            MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .usersRepository(DEFAULT)
                .build())
            .server(configuration -> MemoryServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(new FastViewProbe.Module())
                .overrideWith(Modules.override(new AIBaseModule())
                    .with(binder -> {
                        binder.bind(ApplyWhenFilter.class).toInstance(new SaaSPayingUser(SAAS_REPOSITORY));
                        binder.bind(SaaSAccountRepository.class).toInstance(SAAS_REPOSITORY);
                    }))
                .overrideWith(overrides))
            .build();
    }

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

    MailboxProbeImpl mailboxProbe;
    FastViewProbe fastViewProbe;
    MailboxId inboxId;
    MailboxId archiveId;

    @BeforeEach
    void setUpMailboxes(GuiceJamesServer jamesServer) throws Exception {
        mailboxProbe = jamesServer.getProbe(MailboxProbeImpl.class);
        fastViewProbe = jamesServer.getProbe(FastViewProbe.class);
        inboxId = mailboxProbe.createMailbox(INBOX);
        archiveId = mailboxProbe.createMailbox(ARCHIVE);
        mailboxProbe.createMailbox(MailboxPath.inbox(NON_PAYING_USER));
    }

    static RequestSpecification specificationOf(GuiceJamesServer jamesServer, Username username) {
        return baseRequestSpecBuilder(jamesServer)
            .setBasePath(AI_TOOLS_ENDPOINT)
            .setAuth(authScheme(new UserCredential(username, PASSWORD)))
            .build();
    }

    static void addEligibleUser(GuiceJamesServer jamesServer, Username username) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).addUser(username.asString(), PASSWORD);
        Mono.from(SAAS_REPOSITORY.upsertSaasAccount(username, new SaaSAccount(true, true))).block();
    }

    static String toolCall(String name, String arguments) {
        return """
            {
              "id": "call_1",
              "type": "function",
              "function": { "name": "%s", "arguments": %s }
            }""".formatted(name, arguments);
    }

    static ValidatableResponse post(String body) {
        return given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post()
        .then();
    }

    static ValidatableResponse postTool(String name, String arguments) {
        return post(toolCall(name, arguments));
    }

    static String callTool(String name, String arguments) {
        return postTool(name, arguments)
            .statusCode(SC_OK)
            .contentType("application/json")
            .extract()
            .jsonPath()
            .getString("content");
    }

    static List<String> strings(String content, String path) {
        return JsonPath.from(content).getList(path, String.class);
    }

    static Message.Builder email(String from, String to, String subject, String body) throws Exception {
        return Message.Builder.of()
            .setFrom(from)
            .setTo(to)
            .setSubject(subject)
            .setDate(new Date())
            .generateMessageId(DOMAIN)
            .setBody(body, StandardCharsets.UTF_8);
    }

    ComposedMessageId appendComposed(MailboxPath path, Message.Builder message, Instant receivedAt, Flags flags) throws Exception {
        return mailboxProbe.appendMessage(path.getUser().asString(), path,
            MessageManager.AppendCommand.builder()
                .withInternalDate(Date.from(receivedAt))
                .withFlags(flags)
                .build(message));
    }

    MessageId append(MailboxPath path, Message.Builder message, Instant receivedAt, Flags flags) throws Exception {
        return appendComposed(path, message, receivedAt, flags).getMessageId();
    }

    MessageId append(MailboxPath path, Message.Builder message, Instant receivedAt) throws Exception {
        return append(path, message, receivedAt, new Flags());
    }
}
