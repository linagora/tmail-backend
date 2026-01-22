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

import static com.linagora.tmail.event.TmailEventModule.TMAIL_EVENT_BUS_INJECT_NAME;
import static com.linagora.tmail.listener.rag.LlmMailPrioritizationBackendClassifierListener.LLM_MAIL_CLASSIFIER_CONFIGURATION;
import static org.apache.james.events.EventDeadLettersHealthCheck.DEAD_LETTERS_IGNORED_GROUPS;

import java.io.FileNotFoundException;
import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.events.EventListener;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.Group;
import org.apache.james.modules.mailbox.ListenerConfiguration;
import org.apache.james.modules.mailbox.ListenersConfiguration;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.linagora.tmail.listener.rag.LlmMailPrioritizationBackendClassifierListener;
import com.linagora.tmail.listener.rag.LlmMailPrioritizationClassifierListener;
import com.linagora.tmail.listener.rag.event.AIAnalysisNeededEventSerializer;
import com.linagora.tmail.mailet.AIBotConfig;
import com.linagora.tmail.mailet.AIRedactionalHelper;
import com.linagora.tmail.mailet.LangchainAIRedactionalHelper;
import com.linagora.tmail.mailet.StreamChatLanguageModelFactory;
import com.linagora.tmail.mailet.rag.RagConfig;
import com.linagora.tmail.mailet.rag.RagListener;
import com.linagora.tmail.mailet.rag.httpclient.OpenRagHttpClient;
import com.linagora.tmail.mailet.rag.httpclient.Partition;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;

public class AIBaseModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AIRedactionalHelper.class).to(LangchainAIRedactionalHelper.class);

        Multibinder<Group> deadLetterIgnoredGroups = Multibinder.newSetBinder(binder(), Group.class, Names.named(DEAD_LETTERS_IGNORED_GROUPS));
        deadLetterIgnoredGroups.addBinding().toInstance(RagListener.GROUP);
        deadLetterIgnoredGroups.addBinding().toInstance(LlmMailPrioritizationClassifierListener.GROUP);
        deadLetterIgnoredGroups.addBinding().toInstance(LlmMailPrioritizationBackendClassifierListener.GROUP);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class, Names.named(TMAIL_EVENT_BUS_INJECT_NAME))
            .addBinding()
            .to(LlmMailPrioritizationBackendClassifierListener.class);
    }

    @Provides
    @Singleton
    @Named("ai")
    public Configuration getAIBotConfigurations(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return propertiesProvider.getConfiguration("ai");
    }

    @Provides
    @Singleton
    @Named(LLM_MAIL_CLASSIFIER_CONFIGURATION)
    public HierarchicalConfiguration<ImmutableNode> provideLlmMailPrioritizationListenerConfiguration(ListenersConfiguration listenersConfiguration) {
        return listenersConfiguration.getListenersConfiguration()
            .stream()
            .filter(listener -> listener.getClazz().equals(LlmMailPrioritizationClassifierListener.class.getCanonicalName()))
            .map(ListenerConfiguration::getConfiguration)
            .flatMap(Optional::stream)
            .findFirst()
            .orElseGet(BaseHierarchicalConfiguration::new);
    }

    @ProvidesIntoSet
    public EventSerializer provideAIAnalysisNeededEventSerializer(AIAnalysisNeededEventSerializer aiAnalysisNeededEventSerializer) {
        return aiAnalysisNeededEventSerializer;
    }

    @Provides
    public static AIBotConfig provideAiBotExtensionConfiguration(@Named("ai") Configuration configuration) {
        return AIBotConfig.from(configuration);
    }

    @Provides
    public static RagConfig provideRagConfiguration(@Named("ai") Configuration configuration) {
        return RagConfig.from(configuration);
    }

    @Provides
    @Singleton
    public StreamingChatLanguageModel provideStreamingChatLanguageModel(AIBotConfig config, StreamChatLanguageModelFactory chatLanguageModelFactory) {
        return chatLanguageModelFactory.createChatLanguageModel(config);
    }

    @Provides
    @Singleton
    public OpenRagHttpClient provideOpenRagHttpClient(RagConfig ragConfig) {
        return new OpenRagHttpClient(ragConfig);
    }

    @Provides
    @Singleton
    public Partition.Factory provideRagPartitionFactory(RagConfig ragConfig) {
        return Partition.Factory.fromPattern(ragConfig.getPartitionPattern());
    }
}
