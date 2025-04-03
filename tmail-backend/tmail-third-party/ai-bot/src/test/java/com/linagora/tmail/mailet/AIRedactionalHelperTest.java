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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatLanguageModel;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

public class AIRedactionalHelperTest {
    private AIRedactionalHelper aiRedactioanlHelper;
    private Configuration configuration;
    private AIBotConfig aiBotConfig;

    @BeforeEach
    void setUp() throws Exception{
        aiBotConfig= new AIBotConfig(
            "demo",
            new MailAddress("gpt@localhost"),
            new LlmModel("gemini-2.0-flash"),
            Optional.of(URI.create("https://generativelanguage.googleapis.exemple.com").toURL()));
        ChatLanguageModel chatLanguageModel = new ChatLanguageModelFactory().createChatLanguageModel(aiBotConfig);
        aiRedactioanlHelper = new AIRedactionalHelper(chatLanguageModel);
    }

    @Test
    void testSuggestContentNullInput() {
        assertThatThrownBy(() ->
            aiRedactioanlHelper.suggestContent(null, Optional.of("Valid content")).block()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shoulThrouwOpenAiHttpException() throws Exception {
        configuration.setProperty("baseURL", "https://generativelanguage.googleapis.com");
        aiBotConfig= AIBotConfig.from(configuration);
        ChatLanguageModel chatLanguageModel = new ChatLanguageModelFactory().createChatLanguageModel(aiBotConfig);
        aiRedactioanlHelper = new AIRedactionalHelper(chatLanguageModel);
        String userInput="email content";
        String mailContent="I want to know if your ready to go by 6pm ?";
        assertThatThrownBy(() -> aiRedactioanlHelper.suggestContent(userInput, Optional.of(mailContent)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldReplyToSender() throws Exception {
        String userInput="email content";
        String mailContent="I want to know if your ready to go by 6pm ?";
        String output= aiRedactioanlHelper.suggestContent(userInput, Optional.of(mailContent)).block();;
        assertThat(output).isNotNull();
        assertThat(output).isInstanceOf(String.class);
    }

}
