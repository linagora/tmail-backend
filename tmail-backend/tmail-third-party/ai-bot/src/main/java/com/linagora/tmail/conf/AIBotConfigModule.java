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

package com.linagora.tmail.conf;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.linagora.tmail.mailet.AIBotConfig;
import com.linagora.tmail.mailet.ChatLanguageModelFactory;

import dev.langchain4j.model.chat.ChatLanguageModel;

public class AIBotConfigModule extends AbstractModule {

    @Provides
    @Singleton
    @Named("ai")
    public  Configuration getAIBotConfigurations(PropertiesProvider propertiesProvider) throws FileNotFoundException {
        try {
            return propertiesProvider.getConfiguration("ai.properties");
        } catch (FileNotFoundException | ConfigurationException e) {
            throw new FileNotFoundException("File Not found");
        }
    }

    @Provides
    public static AIBotConfig provideAiBotExtensionConfiguration(@Named("ai") Configuration configuration) {
        return AIBotConfig.fromMailetConfig(configuration);
    }

    @Provides
    @Singleton
    public ChatLanguageModel provideChatLanguageModel(AIBotConfig config, ChatLanguageModelFactory chatLanguageModelFactory) {
        return chatLanguageModelFactory.createChatLanguageModel(config);
    }
}
