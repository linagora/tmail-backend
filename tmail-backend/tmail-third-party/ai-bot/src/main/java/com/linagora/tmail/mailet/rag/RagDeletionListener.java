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

import jakarta.inject.Inject;

import org.apache.james.events.Group;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;

import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.rag.httpclient.OpenRagClient;
import com.linagora.tmail.rag.utils.Partition;

/**
 * Backward-compatible wrapper for the renamed RagDeletionListener.
 *
 * @deprecated Use {@link com.linagora.tmail.rag.listener.RagDeletionListener} instead.
 * This class is kept only for backward compatibility with existing deployments
 * that reference the old fully qualified class name.
 */
@Deprecated
public class RagDeletionListener extends com.linagora.tmail.rag.listener.RagDeletionListener {

    @Inject
    public RagDeletionListener(JmapSettingsRepository jmapSettingsRepository,
                               SessionProvider sessionProvider,
                               MailboxSessionMapperFactory mapperFactory,
                               Partition.Factory partitionFactory,
                               OpenRagClient openRagClient) {
        super(jmapSettingsRepository, sessionProvider, mapperFactory, partitionFactory, openRagClient);
    }

    @Override
    public Group getDefaultGroup() {
        return new Group() {
            @Override
            public String asString() {
                return "com.linagora.tmail.mailet.rag.RagDeletionListener$RagDeletionListenerGroup";
            }
        };
    }
}
