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

package com.linagora.tmail.mailet.rag;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.utils.GuiceLoader;

import com.google.inject.Inject;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.rag.httpclient.OpenRagClient;
import com.linagora.tmail.rag.utils.Partition;

/**
 * Backward-compatible wrapper for the renamed RagListener.
 *
 * @deprecated Use {@link com.linagora.tmail.rag.listener.RagListener} instead.
 * This class is kept only for backward compatibility with existing deployments
 * that reference the old fully qualified class name.
 */
@Deprecated
public class RagListener extends com.linagora.tmail.rag.listener.RagListener {

    @Inject
    public RagListener(MailboxManager mailboxManager,
                       MessageIdManager messageIdManager,
                       SystemMailboxesProvider systemMailboxesProvider,
                       ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm,
                       JmapSettingsRepository jmapSettingsRepository,
                       Partition.Factory partitionFactory,
                       OpenRagClient openRagClient,
                       GuiceLoader guiceLoader,
                       HierarchicalConfiguration<ImmutableNode> configuration) {
        super(mailboxManager, messageIdManager, systemMailboxesProvider,
              threadIdGuessingAlgorithm, jmapSettingsRepository,
              partitionFactory, openRagClient, guiceLoader, configuration);
    }

    @Override
    public Group getDefaultGroup() {
        return new Group() {
            @Override
            public String asString() {
                return "com.linagora.tmail.mailet.rag.RagListener$RagListenerGroup";
            }
        };
    }
}
