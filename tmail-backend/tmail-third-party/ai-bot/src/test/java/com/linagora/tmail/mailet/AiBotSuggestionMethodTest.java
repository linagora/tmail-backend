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

import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.mailet.conf.AIBotModule;
import com.linagora.tmail.mailet.prob.AiBotProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import io.grpc.netty.shaded.io.netty.handler.codec.http.HttpHeaderNames;
import jakarta.mail.internet.MimeMessage;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailets.configuration.*;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Arrays;
import java.util.Optional;

import static io.restassured.RestAssured.with;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.mailets.configuration.Constants.*;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

public class AiBotSuggestionMethodTest {

    static final String DOMAIN = "linagora.com";
    static final String PASSWORD = "secret";

    private static final String BOB = "bob@" + DEFAULT_DOMAIN;
    private static final String ALICE = "alice@" + DEFAULT_DOMAIN;

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    @RegisterExtension
    public TestIMAPClient imapClient = new TestIMAPClient();

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
                }})
            )
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(AiBotProbe.class)))
        .build();

    @Test
    public void shouldLoadAIBot(GuiceJamesServer jamesServer) throws Exception {
        AIRedactionalHelper helperr =jamesServer.getProbe(AiBotProbe.class).getAiRedactionalHelper();
        String result= helperr.suggestContent("aaa", Optional.of("alae")).block();
        System.out.println(result);
        assertThat(result).isNotNull();
    }

    @Test
    public void shouldSendEmail(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(MailboxManagerClassProbe.class).getMailboxManagerClass())
            .isEqualTo(InMemoryMailboxManager.class);
    }

    @Test
    public void shoudVerifyServerStarted(GuiceJamesServer jamesServer) throws Exception {
        Assertions.assertTrue(jamesServer.isStarted());
    }
    @Test
    public void shoudVerifyEmailSent(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(BOB, PASSWORD)
            .addUser(ALICE, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(ALICE)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipient(ALICE));
        imapClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(ALICE, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(Constants.awaitAtMostOneMinute, 1);

        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(imapClient.readFirstMessage());
        System.out.println(Arrays.toString(mimeMessage.getHeader("Message-Id")));
        assertThat(mimeMessage.getHeader("Sender")).containsOnly(BOB);
        assertThat(mimeMessage.getHeader("Subject")[0]).isEqualTo("How can I cook an egg?");
    }
    @Test
    public void shoudVerifyJmapMethod(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(BOB, PASSWORD)
            .addUser(ALICE, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(BOB)
                    .addToRecipient(ALICE)
                    .setSubject("How can I cook an egg?")
                    .setText("I do not know how to cook an egg. Please help me."))
                .sender(BOB)
                .recipient(ALICE));
        imapClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(ALICE, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(Constants.awaitAtMostOneMinute, 1);
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(imapClient.readFirstMessage());

        String request = "{\n" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:aibot\"],\n" +
            "  \"methodCalls\": [\n" +
            "    [\"AiBot/Suggest\", {\n" +
            "      \"accountId\": \"ec77d4733cef70537b19b580db81fbbb6dc2a46a6d5662055cab568e75f71570\",\n" +
            "      \"userInput\": \"explain to him how to cook an egg\",\n" +
            "      \"emailId\": " + Arrays.toString(mimeMessage.getHeader("Message-Id")) + "\n" +
            "    }, \"0\"]\n" +
            "  ]\n" +
            "}";
        System.out.println("Generated Request: \n" + request);

        with()
            .header(HttpHeaderNames.ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER())
            .body(request)
            .when()
            .post();
    }
}
