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

import static com.linagora.tmail.mailet.AIBotConfig.DEFAULT_TIMEOUT;

import java.net.URI;
import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;

@Disabled("Requires a valid API key in order to be run")
class AIBotMailetTest {
    public static MailAddress createMailAddress(String mailAddress) {
        try {
            return new MailAddress(mailAddress);
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }

    public static final String DEMO_MODEL = "gpt-4o-mini";
    private static final MailAddress ASKING_SENDER = createMailAddress("sender@example.com");
    private static final MailAddress BOT_ADDRESS = createMailAddress("gpt@localhost");

    private MailetContext mailetContext;
    private AIBotConfig aiBotConfig;
    private MailAddress botAddress;
    private AIBotMailet testee;

    @BeforeEach
    void setUp() throws Exception {
        aiBotConfig = new AIBotConfig(
            "sk-fakefakefakefakefakefakefakefake",
            new LlmModel("lucie-7b-instruct-v1.1"),
            Optional.of(URI.create("https://chat.lucie.ovh.linagora.com/v1/").toURL()),
            DEFAULT_TIMEOUT);
        botAddress = new MailAddress("gpt@localhost");
        StreamChatLanguageModelFactory streamChatLanguageModelFactory = new StreamChatLanguageModelFactory();
        StreamingChatLanguageModel chatLanguageModel = streamChatLanguageModelFactory.createChatLanguageModel(aiBotConfig);
        testee = new AIBotMailet(aiBotConfig, botAddress, chatLanguageModel, new JsoupHtmlTextExtractor());
        mailetContext = Mockito.mock(MailetContext.class);
    }

    @Test
    void shouldReplyToSender() throws Exception {

        testee.init(FakeMailetConfig
            .builder()
            .mailetContext(mailetContext)
            .build());
        Mail mail = MailImpl.builder()
            .name("mail-id")
            .sender(ASKING_SENDER)
            .addRecipient(MailAddressFixture.OTHER_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("How can I cook an egg?")
                .setText("I do not know how to cook an egg. Please help me.")
                .build())
            .build();
        testee.service(mail);
        Mockito.verify(mailetContext).sendMail(ArgumentMatchers.eq(BOT_ADDRESS), ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}
