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
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.core.MailAddress;

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

    public static AIBotConfig fromMailetConfig(Configuration configuration) throws IllegalArgumentException {
        String apiKeyParam = configuration.getString(API_KEY_PARAMETER_NAME,"");
        String gptAddressParam = configuration.getString(BOT_ADDRESS_PARAMETER_NAME,"");
        String llmModelParam = configuration.getString(MODEL_PARAMETER_NAME,"");
        String baseUrlParam = configuration.getString(BASE_URL_PARAMETER_NAME,"");


        if (Strings.isNullOrEmpty(apiKeyParam)) {
            throw new IllegalArgumentException("No value for " + API_KEY_PARAMETER_NAME + " parameter was provided.");
        }

        if (Strings.isNullOrEmpty(gptAddressParam)) {
            throw new IllegalArgumentException("No value for " + BOT_ADDRESS_PARAMETER_NAME + " parameter was provided.");
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AIBotConfig that = (AIBotConfig) o;
        return Objects.equals(apiKey, that.apiKey) &&
            Objects.equals(botAddress, that.botAddress) &&
            Objects.equals(llmModel, that.llmModel) &&
            Objects.equals(baseURLOpt.map(URL::toString), that.baseURLOpt.map(URL::toString));
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey, botAddress, llmModel, baseURLOpt.map(URL::toString));
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
