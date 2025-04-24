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
import com.linagora.tmail.mailet.conf.AIBotModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.restassured.specification.RequestSpecification;
import jakarta.mail.Flags;
import jakarta.mail.internet.MimeMessage;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Date;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.rfc8621.contract.Fixture.*;

public class AiBotSuggestionMethodTest {

    static final String DOMAIN = "james.org";
    static final String PASSWORD = "secret";

    private static final String BOB = "bob@" + DOMAIN;
    private static final String ALICE = "alice@" + DOMAIN;

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
            .overrideWith(Modules.override(new AIBotModule()).with(new AIBotModule(){
                @Override
                protected void configure() {
                    bind(AIRedactionalHelper.class).to(AIRedactionalHelperForTest.class);
                }})))
        .build();


    @Disabled("Capability not supported yet")
    @Test
    public void shoudVerifyJmapMethodReplyToEmailFromScratch(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(ALICE, PASSWORD);

        MailboxPath path = MailboxPath.inbox(Username.of(ALICE));
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(path);

        String request = "{\n" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\": \"ec77d4733cef70537b19b580db81fbbb6dc2a46a6d5662055cab568e75f71570\"," +
            "      \"userInput\": \"explain to him how to cook an egg\"" +
            "    }, \"0\"]" +
            "  ]" +
            "}";

        //assertions are not complete yet
        given(buildJmapRequestSpecification(Username.of(ALICE), PASSWORD, jamesServer))
            .log().all()
            .body(String.format(request))
            .when()
            .post()
            .then()
            .log().all()
            .statusCode(SC_OK);
    }

    @Disabled("Capability not supported yet")
    @Test
    public void shoudVerifyJmapMethod(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain("james.org")
            .addUser(ALICE, PASSWORD);

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setSender(BOB)
            .addToRecipient("alice@james.org")
            .setSubject("Redaction Aide")
            .setText("Peux-tu m’aider à écrire une réponse formelle ?")
            .build();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(outputStream);
        InputStream messageInputStream = new ByteArrayInputStream(outputStream.toByteArray());
        MailboxPath path = MailboxPath.inbox(Username.of(ALICE));
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(path);
        MessageId messageId = jamesServer.getProbe(MailboxProbeImpl.class).appendMessage(ALICE,path,messageInputStream,new Date(),true,new Flags(Flags.Flag.RECENT)).getMessageId();
        String request = "{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"]," +
            "  \"methodCalls\": [" +
            "    [\"AiBot/Suggest\", {" +
            "      \"accountId\":\\\"ec77d4733cef70537b19b580db81fbbb6dc2a46a6d5662055cab568e75f71570\\\"," +
            "      \"userInput\": \"explain to him how to cook an egg\"," +
            "      \"emailId\": \"" + messageId.serialize() + "\"" +
            "    }, \"0\"]" +
            "  ]" +
            "}";

        //assertions are not complete yet
        given(buildJmapRequestSpecification(Username.of(ALICE), PASSWORD, jamesServer))
            .log().all()
            .body(String.format(request))
            .when()
            .post()
            .then()
            .log().all()
            .statusCode(SC_OK);
    }

    private RequestSpecification buildJmapRequestSpecification(Username username, String password, GuiceJamesServer jamesServer) {
        return baseRequestSpecBuilder(jamesServer)
            .setAuth(authScheme(new UserCredential(username, password)))
            .addHeader(HttpHeaderNames.ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER())
            .build();
    }
}

