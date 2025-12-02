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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class AIBotConfig {
    public static String API_KEY_PARAMETER_NAME = "apiKey";
    public static String MODEL_PARAMETER_NAME = "model";
    public static String BASE_URL_PARAMETER_NAME = "baseURL";
    public static String TIMEOUT_PARAMETER_NAME = "timeout";

    public static final LlmModel DEFAULT_LLM_MODEL =
        new LlmModel("gpt-4o-mini");
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final String apiKey;
    private final Optional<URL> baseURLOpt;
    private final LlmModel llmModel;
    private final Duration timeout;

    public AIBotConfig(String apiKey, LlmModel llmModel, Optional<URL> baseURLOpt, Duration timeout) {
        Preconditions.checkNotNull(apiKey);
        Preconditions.checkNotNull(llmModel);
        Preconditions.checkNotNull(baseURLOpt);

        this.apiKey = apiKey;
        this.baseURLOpt = baseURLOpt;
        this.llmModel = llmModel;
        this.timeout = timeout;
    }

    public static AIBotConfig from(Configuration configuration) throws IllegalArgumentException {
        String apiKeyParam = Optional.ofNullable(configuration.getString(API_KEY_PARAMETER_NAME, null))
            .orElseThrow(() ->  new IllegalArgumentException("No value for " + API_KEY_PARAMETER_NAME + " parameter was provided."));
        LlmModel llmModelParam = Optional.ofNullable(configuration.getString(MODEL_PARAMETER_NAME))
            .filter(modelString -> !Strings.isNullOrEmpty(modelString))
            .map(LlmModel::new).orElse(DEFAULT_LLM_MODEL);
        String baseUrlParam = Optional.ofNullable(configuration.getString(BASE_URL_PARAMETER_NAME,null))
            .orElseThrow(() ->  new IllegalArgumentException("No value for " + BASE_URL_PARAMETER_NAME + " parameter was provided."));

        Optional<URL> baseURLOpt = Optional.ofNullable(baseUrlParam)
            .filter(baseUrlString -> !Strings.isNullOrEmpty(baseUrlString))
            .flatMap(AIBotConfig::baseURLStringToURL);

        Duration timeout = Optional.ofNullable(configuration.getString(TIMEOUT_PARAMETER_NAME, null))
            .map(value -> DurationParser.parse(value, ChronoUnit.SECONDS))
            .orElse(DEFAULT_TIMEOUT);


        try {
            return new AIBotConfig(apiKeyParam, llmModelParam, baseURLOpt, timeout);
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
            Objects.equals(llmModel, that.llmModel) &&
            Objects.equals(Optional.ofNullable(baseURLOpt).map(opt -> opt.map(URL::toString)),
                Optional.ofNullable(that.baseURLOpt).map(opt -> opt.map(URL::toString))) &&
            Objects.equals(timeout, that.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey, llmModel,
            Optional.ofNullable(baseURLOpt).map(opt -> opt.map(URL::toString)),
            timeout);
    }

    public String getApiKey() {
        return apiKey;
    }

    public LlmModel getLlmModel() {
        return llmModel;
    }

    public Optional<URL> getBaseURL() {
        return baseURLOpt;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
