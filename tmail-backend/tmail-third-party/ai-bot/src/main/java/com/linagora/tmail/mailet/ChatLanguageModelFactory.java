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

import java.net.URL;
import java.util.Optional;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class ChatLanguageModelFactory {
    private static final String USE_DEFAULT_BASE_URL = "";

    public ChatLanguageModel createChatLanguageModel(AIBotConfig config) {
        String apiKey = config.getApiKey();
        LlmModel llmModel = config.getLlmModel();
        Optional<URL> baseURLOpt = config.getBaseURL();

        return createOpenAILanguageModel(apiKey, llmModel.modelName(), baseURLOpt.map(URL::toString).orElse(USE_DEFAULT_BASE_URL));
    }

    private ChatLanguageModel createOpenAILanguageModel(String apiKey, String modelName, String baseUrl) {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .build();
    }
}
