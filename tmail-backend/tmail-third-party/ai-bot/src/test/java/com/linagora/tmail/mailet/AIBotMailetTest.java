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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.mail.internet.AddressException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
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

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Optional;

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
    private AIBotMailet testee;

    @BeforeEach
    void setUp() throws MalformedURLException, AddressException {
        aiBotConfig= new AIBotConfig(
            "demo",
            new MailAddress("gpt@localhost"),
            new LlmModel("gemini-2.0-flash"),
            Optional.of(URI.create("https://generativelanguage.googleapis.exemple.com").toURL()));
        ChatLanguageModelFactory chatLanguageModelFactory = new ChatLanguageModelFactory();
        ChatLanguageModel chatLanguageModel= chatLanguageModelFactory.createChatLanguageModel(aiBotConfig);
        testee = new AIBotMailet(aiBotConfig, chatLanguageModel, new JsoupHtmlTextExtractor());
        mailetContext = Mockito.mock(MailetContext.class);
    }

    @Disabled("openai account quota limit issue")
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
