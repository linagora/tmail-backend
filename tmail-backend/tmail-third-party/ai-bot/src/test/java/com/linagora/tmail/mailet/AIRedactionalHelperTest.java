package com.linagora.tmail.mailet;



import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import org.apache.james.core.MailAddress;

import org.apache.mailet.MailetContext;
import org.apache.mailet.MailetException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;


public class AIRedactionalHelperTest {
    public static MailAddress createMailAddress(String mailAddress) {
        try {
            return new MailAddress(mailAddress);
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }
    public static final String DEMO_MODEL = "lucie-7b-instruct-v1.1";
    private static final MailAddress ASKING_SENDER = createMailAddress("sender@example.com");

    private AIRedactioanlHelper aiRedactioanlHelper;
    private MailetContext mailetContext;
    @BeforeEach
    void setUp() {
        aiRedactioanlHelper = new AIRedactioanlHelper(new ChatLanguageModelFactory());
        mailetContext = mock(MailetContext.class);
    }
    @Test
    void should_check_if_redactional_model_exists() throws MailetException {
        assertThat(aiRedactioanlHelper).isNotNull();
    }
    @Test
    void testSuggestContentNullInput() {
        ChatLanguageModelFactory mockFactory = mock(ChatLanguageModelFactory.class);
        AIRedactioanlHelper helper = new AIRedactioanlHelper(mockFactory);

        assertThrows(IllegalArgumentException.class, () ->
                helper.suggestContent(null, "Valid content").block());
    }

    @Test
    void initShouldCheckChatModel() throws MessagingException , IOException {
        aiRedactioanlHelper.confuguerAiBot();
        assertThat(aiRedactioanlHelper.getChatLanguageModel()).isNotNull();
    }
    @Test
    void shoulthrouwOpenAiHttpException() throws Exception {


        String userInput="email content";
        String mailContent="I want to know if your ready to go by 6pm ?";
        assertThatThrownBy(() -> aiRedactioanlHelper.suggestContent(userInput, mailContent))
                .isInstanceOf(RuntimeException.class);
    }
    @Test
    void shouldReplyToSender() throws Exception {

        String userInput="email content";
        String mailContent="I want to know if your ready to go by 6pm ?";
        String output= aiRedactioanlHelper.suggestContent(userInput,mailContent).block();;
        assertThat(output).isNotNull();
        assertThat(output).isInstanceOf(String.class);
    }

}
