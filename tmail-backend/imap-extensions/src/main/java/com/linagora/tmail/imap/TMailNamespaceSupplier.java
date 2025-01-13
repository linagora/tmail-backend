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

package com.linagora.tmail.imap;

import java.util.Collection;

import org.apache.james.imap.message.response.NamespaceResponse;
import org.apache.james.imap.message.response.NamespaceResponse.Namespace;
import org.apache.james.imap.processor.NamespaceSupplier;
import org.apache.james.mailbox.MailboxSession;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.team.TeamMailboxNameSpace;

public class TMailNamespaceSupplier implements NamespaceSupplier {

    @Override
    public Collection<Namespace> personalNamespaces(MailboxSession session) {
        return ImmutableList.of(new NamespaceResponse.Namespace("", session.getPathDelimiter()));
    }

    @Override
    public Collection<Namespace> otherUsersNamespaces(MailboxSession session) {
        return ImmutableList.of(new NamespaceResponse.Namespace("#user", session.getPathDelimiter()));
    }

    @Override
    public Collection<Namespace> sharedNamespaces(MailboxSession session) {
        return ImmutableList.of(new NamespaceResponse.Namespace(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE(), session.getPathDelimiter()));
    }
}
