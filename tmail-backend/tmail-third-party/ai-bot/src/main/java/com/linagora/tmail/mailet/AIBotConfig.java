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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import com.linagora.tmail.conf.AIBotProperties;
import org.apache.james.core.MailAddress;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetException;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;


public class AIBotConfig {
    public static String API_KEY_PARAMETER_NAME = "apiKey";
    public static String BOT_ADDRESS_PARAMETER_NAME = "botAddress";
    public static String MODEL_PARAMETER_NAME = "model";
    public static String BASE_URL_PARAMETER_NAME = "baseURL";

    public static final LlmModel DEFAULT_LLM_MODEL =
        new LlmModel("gpt-4o-mini");

    private final String apiKey;
    private final Optional<URL> baseURLOpt;
    private final MailAddress botAddress;
    private final LlmModel llmModel;

    public AIBotConfig(String apiKey, MailAddress botAddress, LlmModel llmModel, Optional<URL> baseURLOpt) {
        Preconditions.checkNotNull(apiKey);
        Preconditions.checkNotNull(botAddress);
        Preconditions.checkNotNull(llmModel);
        Preconditions.checkNotNull(baseURLOpt);

        this.apiKey = apiKey;
        this.baseURLOpt = baseURLOpt;
        this.botAddress = botAddress;
        this.llmModel = llmModel;
    }

    public static AIBotConfig fromMailetConfig( AIBotProperties aiBotProperties) throws MailetException {
        String apiKeyParam = aiBotProperties.getApiKey();
        String gptAddressParam = aiBotProperties.getBotAddress();
        String llmModelParam = aiBotProperties.getModel();
        String baseUrlParam = aiBotProperties.getBaseUrl()
                .map(URL::toString) // Convertir l'URL en String
                .orElse("");
        if (Strings.isNullOrEmpty(apiKeyParam)) {
            throw new MailetException("No value for " + API_KEY_PARAMETER_NAME + " parameter was provided.");
        }

        if (Strings.isNullOrEmpty(gptAddressParam)) {
            throw new MailetException("No value for " + BOT_ADDRESS_PARAMETER_NAME + " parameter was provided.");
        }

        Optional<URL> baseURLOpt = Optional.ofNullable(baseUrlParam)
            .filter(baseUrlString -> !Strings.isNullOrEmpty(baseUrlString))
            .flatMap(AIBotConfig::baseURLStringToURL);

        LlmModel llmModel = Optional.ofNullable(llmModelParam)
            .filter(modelString -> !Strings.isNullOrEmpty(modelString))
            .map(LlmModel::new).orElse(DEFAULT_LLM_MODEL);

        try {
            return new AIBotConfig(apiKeyParam, new MailAddress(gptAddressParam), llmModel, baseURLOpt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static Optional<URL> baseURLStringToURL(String baseUrlString) {
        try {
            return Optional.of(URI.create(baseUrlString).toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid LLM API base URL", e);
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public MailAddress getBotAddress() {
        return botAddress;
    }

    public LlmModel getLlmModel() {
        return llmModel;
    }

    public Optional<URL> getBaseURL() {
        return baseURLOpt;
    }
}
