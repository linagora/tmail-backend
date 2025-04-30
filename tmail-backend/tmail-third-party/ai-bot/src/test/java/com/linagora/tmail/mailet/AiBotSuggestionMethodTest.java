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

package com.linagora.tmail.mailet;

import com.google.inject.util.Modules;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.jmap.aibot.AiBotMethodModule;
import com.linagora.tmail.mailet.conf.AIBotModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.mail.Flags;
import jakarta.mail.internet.MimeMessage;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.rfc8621.contract.Fixture.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import org.apache.james.mime4j.dom.Message;

public class AiBotSuggestionMethodTest {
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
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(Modules.override(new AIBotModule()).with(binder -> binder.bind(AIRedactionalHelper.class).to(AIRedactionalHelperForTest.class)))
            .overrideWith(new AiBotMethodModule()))
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

    @Test
    public void shoudSuggestContentWhenEmailIdProvided(GuiceJamesServer jamesServer) throws Exception {
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setSender(BOB)
            .addToRecipient("alice@james.org")
            .setSubject("Asking for help")
            .setText("How to cook an egg ?")
            .build();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(outputStream);
        InputStream messageInputStream = new ByteArrayInputStream(outputStream.toByteArray());
        MessageId messageId = jamesServer.getProbe(MailboxProbeImpl.class).appendMessage(ALICE, path, messageInputStream, new Date(), true, new Flags(Flags.Flag.RECENT)).getMessageId();

        String request = String.format("{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\":\"%s\"," +
            "      \"userInput\": \"explain to him how to cook an egg\"," +
            "      \"emailId\": \"" + messageId.serialize() + "\"" +
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
            .body("methodResponses[0][1].suggestion", equalTo("This suggestion is just for testing purpose this is your UserInput: explain to him how to cook an egg This is you mailContent: How to cook an egg ?"));
    }

    @Test
    public void shouldExtractOnlyTextContentFromEmail(GuiceJamesServer jamesServer) throws Exception {
        BasicBodyFactory bodyFactory = new BasicBodyFactory();
        Message message = Message.Builder.of()
            .setFrom("John Doe <jdoe@machine.example>")
            .setTo("Mary Smith <mary@example.net>")
            .setSubject("An image for you")
            .setDate(new Date())
            .generateMessageId(InetAddress.getLocalHost().getCanonicalHostName())
            .setBody(MultipartBuilder.create("alternative")
                .setPreamble("This is a multi_part message in mime format")
                .addTextPart("This is the text part ", StandardCharsets.UTF_8)
                .addBodyPart(BodyPartBuilder.create().setBody(bodyFactory.textBody("<html><body><h1>Hello World in HTML</h1></body></html>", "UTF-8"))
                    .setContentType("text/html"))
                .build()
                )
            .build();

        MessageId messageId = jamesServer.getProbe(MailboxProbeImpl.class).appendMessage(ALICE, path, MessageManager.AppendCommand.from(message)).getMessageId();
        String request = String.format("{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\":\"%s\"," +
            "      \"userInput\": \"explain to him how to cook an egg\"," +
            "      \"emailId\": \"" + messageId.serialize() + "\"" +
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
            .body("methodResponses[0][1].suggestion", equalTo("This suggestion is just for testing purpose this is your UserInput: explain to him how to cook an egg This is you mailContent: This is the text part"));
    }

    @Test
    public void shouldExtractTextContentWhenMailHasAttachment(GuiceJamesServer jamesServer) throws Exception {
        BasicBodyFactory bodyFactory = new BasicBodyFactory();
        Message message = Message.Builder.of()
            .setFrom("John Doe <jdoe@machine.example>")
            .setTo("Mary Smith <mary@example.net>")
            .setSubject("An attacheemnt for you")
            .setDate(new Date())
            .generateMessageId(InetAddress.getLocalHost().getCanonicalHostName())
            .setBody(MultipartBuilder.create("mixed")
                .setPreamble("This is a multi_part message with an attachment")
                .addTextPart("This is the text part ", StandardCharsets.UTF_8)
                .addBodyPart(BodyPartBuilder.create()
                    .setBody(bodyFactory.binaryBody("This is the content of the attachment".getBytes(StandardCharsets.UTF_8)))
                    .setContentType("application/octet-stream")
                    .setContentDisposition("attachment; filename=\"example.txt\""))
                .build())
            .build();

        MessageId messageId = jamesServer.getProbe(MailboxProbeImpl.class)
            .appendMessage(ALICE, path, MessageManager.AppendCommand.from(message))
            .getMessageId();

        String request = String.format("{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\":\"%s\"," +
            "      \"userInput\": \"explain to him how to cook an egg\"," +
            "      \"emailId\": \"" + messageId.serialize() + "\"" +
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
            .body("methodResponses[0][1].suggestion", equalTo("This suggestion is just for testing purpose this is your UserInput: explain to him how to cook an egg This is you mailContent: This is the text part"));
    }

    @Test
    public void shouldReturTextContentNothingWhenEmailHasNoTextPart(GuiceJamesServer jamesServer) throws Exception {
        BasicBodyFactory bodyFactory = new BasicBodyFactory();
        Message message = Message.Builder.of()
            .setFrom("John Doe <jdoe@machine.example>")
            .setTo("Mary Smith <mary@example.net>")
            .setSubject("An image for you")
            .setDate(new Date())
            .generateMessageId(InetAddress.getLocalHost().getCanonicalHostName())
            .setBody(MultipartBuilder.create("mixed")
                .setPreamble("This is a multi_part message with an attachment")
                .addBodyPart(BodyPartBuilder.create()
                    .setBody(bodyFactory.binaryBody("This is the content of the attachment".getBytes(StandardCharsets.UTF_8)))
                    .setContentType("application/octet-stream")
                    .setContentDisposition("attachment; filename=\"example.txt\""))
                .build())
            .build();

        MessageId messageId = jamesServer.getProbe(MailboxProbeImpl.class)
            .appendMessage(ALICE, path, MessageManager.AppendCommand.from(message))
            .getMessageId();

        String request = String.format("{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\":\"%s\"," +
            "      \"userInput\": \"explain to him how to cook an egg\"," +
            "      \"emailId\": \"" + messageId.serialize() + "\"" +
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
            .body("methodResponses[0][1].suggestion", equalTo("This suggestion is just for testing purpose this is your UserInput: explain to him how to cook an egg This is you mailContent: "))
            .body("methodResponses[0][1].suggestion", CoreMatchers.not(containsString("This is the content of the attachment")));
    }

    @Test
    public void shouldReturnUnknownMethodWhenMissingAiCapability(GuiceJamesServer jamesServer) throws Exception {
        String request = String.format("{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\":\"%s\"," +
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
            .body("methodResponses[0][0]", equalTo("error"))
            .body("methodResponses[0][1].type", equalTo("unknownMethod"))
            .body("methodResponses[0][1].description", equalTo("Missing capability(ies): com:linagora:params:jmap:aibot"));
    }

    @Test
    public void shouldReturnUnknownMethodWhenMissingCoreCapability(GuiceJamesServer jamesServer) throws Exception {
        String request = String.format("{" +
            "  \"using\": [\"com:linagora:params:jmap:aibot\"]," +
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
            .body("methodResponses[0][0]", equalTo("error"))
            .body("methodResponses[0][1].type", equalTo("unknownMethod"))
            .body("methodResponses[0][1].description", equalTo("Missing capability(ies): urn:ietf:params:jmap:core"));
    }

    @Test
    public void shoudReturnInvalidargumentWhenMissingAccountId(GuiceJamesServer jamesServer) throws Exception {
        String request ="{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"userInput\": \"explain to him how to cook an egg\"" +
            "    }, \"0\"]" +
            "  ]" +
            "}";

        given(buildJmapRequestSpecification(Username.of(ALICE), PASSWORD, jamesServer))
            .body(String.format(request))
        .when()
            .post()
        .then()
            .statusCode(SC_OK)
            .body("methodResponses[0][0]", equalTo("error"))
            .body("methodResponses[0][1].type", equalTo("invalidArguments"))
            .body("methodResponses[0][1].description", equalTo("missing accountId"));
    }

    @Test
    public void shoudReturnInvalidargumentWhenMissingUserinput(GuiceJamesServer jamesServer) throws Exception {
        String request = String.format("{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\": \"%s\"" +
            "    }, \"0\"]" +
            "  ]" +
            "}", accountId);

        given(buildJmapRequestSpecification(Username.of(ALICE), PASSWORD, jamesServer))
            .body(String.format(request))
        .when()
            .post()
        .then()
            .statusCode(SC_OK)
            .body("methodResponses[0][0]", equalTo("error"))
            .body("methodResponses[0][1].type", equalTo("invalidArguments"))
            .body("methodResponses[0][1].description", equalTo("missing UserInput"));
    }

    @Test
    public void shouldReturnInvalidMailIdWhenWrongFormat(GuiceJamesServer jamesServer) throws Exception {
        String messageId = "wrongFormat";
        String request = String.format("{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\":\"%s\"," +
            "      \"userInput\": \"explain to him how to cook an egg\"," +
            "      \"emailId\": \"" + messageId + "\"" +
            "    }, \"0\"]" +
            "  ]" +
            "}", accountId);

        given(buildJmapRequestSpecification(Username.of(ALICE), PASSWORD, jamesServer))
            .body(String.format(request))
        .when()
            .post()
        .then()
            .statusCode(SC_OK)
            .body("methodResponses[0][0]", equalTo("error"))
            .body("methodResponses[0][1].type", equalTo("invalidArguments"))
            .body("methodResponses[0][1].description", equalTo("For input string: \"wrongFormat\""));
    }

    @Test
    public void shouldReturnMailNotFoundWhenWrongMailId(GuiceJamesServer jamesServer) throws Exception {
        String request = String.format("{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\":\"%s\"," +
            "      \"userInput\": \"explain to him how to cook an egg\"," +
            "      \"emailId\": \"" + "2" + "\"" +
            "    }, \"0\"]" +
            "  ]" +
            "}", accountId);

        given(buildJmapRequestSpecification(Username.of(ALICE), PASSWORD, jamesServer))
            .body(String.format(request))
        .when()
            .post()
        .then()
            .statusCode(SC_OK)
            .body("methodResponses[0][0]", equalTo("error"))
            .body("methodResponses[0][1].type", equalTo("invalidArguments"))
            .body("methodResponses[0][1].description", equalTo("MessageId not found: 2"));
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