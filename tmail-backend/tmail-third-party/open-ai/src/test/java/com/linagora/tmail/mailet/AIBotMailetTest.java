package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

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
    private static final MailAddress GPT_ADDRESS = createMailAddress("gpt@example.com");

    private AIBotMailet testee;
    private MailetContext mailetContext;

    @BeforeEach
    void setUp() {
        testee = new AIBotMailet(new ChatLanguageModelFactory(), new JsoupHtmlTextExtractor());
        mailetContext = Mockito.mock(MailetContext.class);
    }

    @Test
    void initShouldThrowWhenMissingApiKeyProperty() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig
            .builder()
            .setProperty("gptAddress", GPT_ADDRESS.asString())
            .mailetContext(mailetContext)
            .build()))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void initShouldThrowWhenMissingGptAddressProperty() {
        assertThatThrownBy(() -> testee.init(FakeMailetConfig
            .builder()
            .setProperty("apiKey", "demo")
            .mailetContext(mailetContext)
            .build()))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void shouldReplyToSender() throws Exception {
        testee.init(FakeMailetConfig
            .builder()
            .setProperty("apiKey", "demo")
            .setProperty("gptAddress", GPT_ADDRESS.asString())
            .setProperty("model", DEMO_MODEL)
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

        Mockito.verify(mailetContext).sendMail(ArgumentMatchers.eq(GPT_ADDRESS), ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}
