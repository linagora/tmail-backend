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

import static com.linagora.tmail.event.TmailEventModule.TMAIL_EVENT_BUS_INJECT_NAME;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.events.EventBus;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.utils.GuiceLoader;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;

/**
 * Backward-compatible wrapper for the renamed LlmMailClassifierListener.
 *
 * @deprecated Use {@link com.linagora.tmail.classifier.listener.LlmMailClassifierListener} instead.
 * This class is kept only for backward compatibility with existing deployments
 * that reference the old fully qualified class name.
 */
@Deprecated
public class LlmMailClassifierListener extends com.linagora.tmail.classifier.listener.LlmMailClassifierListener {

    @Inject
    public LlmMailClassifierListener(MailboxManager mailboxManager,
                                      MessageIdManager messageIdManager,
                                      SystemMailboxesProvider systemMailboxesProvider,
                                      JmapSettingsRepository jmapSettingsRepository,
                                      GuiceLoader guiceLoader,
                                      @Named(TMAIL_EVENT_BUS_INJECT_NAME) EventBus tmailEventBus,
                                      HierarchicalConfiguration<ImmutableNode> configuration) {
        super(mailboxManager, messageIdManager, systemMailboxesProvider,
              jmapSettingsRepository, guiceLoader, tmailEventBus, configuration);
    }

    @Override
    public Group getDefaultGroup() {
        return new Group() {
            @Override
            public String asString() {
                return "com.linagora.tmail.listener.rag.LlmMailClassifierListener$LlmMailPrioritizationClassifierGroup";
            }
        };
    }
}
