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

package com.linagora.tmail.listener.rag;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.events.Group;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.html.HtmlTextExtractor;

import com.google.inject.name.Named;
import com.linagora.tmail.classifier.prompt.PromptRetriever;
import com.linagora.tmail.james.jmap.label.LabelRepository;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;

/**
 * Backward-compatible wrapper for the renamed LlmMailBackendClassifierListener.
 *
 * @deprecated Use {@link com.linagora.tmail.classifier.listener.LlmMailBackendClassifierListener} instead.
 * This class is kept only for backward compatibility with existing deployments
 * that reference the old fully qualified class name.
 */
@Deprecated
public class LlmMailBackendClassifierListener extends com.linagora.tmail.classifier.listener.LlmMailBackendClassifierListener {

    @Inject
    public LlmMailBackendClassifierListener(MailboxManager mailboxManager,
                                            MessageIdManager messageIdManager,
                                            StreamingChatLanguageModel chatLanguageModel,
                                            HtmlTextExtractor htmlTextExtractor,
                                            IdentityRepository identityRepository,
                                            MetricFactory metricFactory,
                                            LabelRepository labelRepository,
                                            @Named(LLM_MAIL_CLASSIFIER_CONFIGURATION) HierarchicalConfiguration<ImmutableNode> configuration,
                                            PromptRetriever.Factory promptRetrieverFactory) {
        super(mailboxManager, messageIdManager, chatLanguageModel, htmlTextExtractor,
              identityRepository, metricFactory, labelRepository, configuration, promptRetrieverFactory);
    }

    @Override
    public Group getDefaultGroup() {
        return new Group() {
            @Override
            public String asString() {
                return "com.linagora.tmail.listener.rag.LlmMailBackendClassifierListener$LlmMailPrioritizationBackendClassifierGroup";
            }
        };
    }
}
