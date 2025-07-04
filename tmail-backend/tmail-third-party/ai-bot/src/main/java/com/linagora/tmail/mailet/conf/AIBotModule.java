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

package com.linagora.tmail.mailet.conf;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.linagora.tmail.mailet.AIBotConfig;
import com.linagora.tmail.mailet.AIRedactionalHelper;
import com.linagora.tmail.mailet.LangchainAIRedactionalHelper;
import com.linagora.tmail.mailet.StreamChatLanguageModelFactory;
import com.linagora.tmail.mailet.rag.RagListenerConfiguration;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;

public class AIBotModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AIRedactionalHelper.class).to(LangchainAIRedactionalHelper.class);
    }

    @Provides
    @Singleton
    @Named("ai")
    public  Configuration getAIBotConfigurations(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return propertiesProvider.getConfiguration("ai");
    }

    @Provides
    @Singleton
    @Named("rag")
    public  Configuration getRAGConfigurations(ConfigurationProvider configurationProvider) throws ConfigurationException {
        return configurationProvider.getConfiguration("listeners");
    }

    @Provides
    public static AIBotConfig provideAiBotExtensionConfiguration(@Named("ai") Configuration configuration) {
        return AIBotConfig.from(configuration);
    }

    @Provides
    public RagListenerConfiguration provideRagExtensionConfiguration(@Named("rag") Configuration configuration) throws ConfigurationException {
        return RagListenerConfiguration.from(configuration);
    }

    @Provides
    @Singleton
    public StreamingChatLanguageModel provideStreamingChatLanguageModel(AIBotConfig config, StreamChatLanguageModelFactory chatLanguageModelFactory) {
        return chatLanguageModelFactory.createChatLanguageModel(config);
    }
}
