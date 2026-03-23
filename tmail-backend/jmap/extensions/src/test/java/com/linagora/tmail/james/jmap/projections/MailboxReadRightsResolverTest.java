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

package com.linagora.tmail.james.jmap.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.model.MailboxACL;
import org.junit.jupiter.api.Test;

class MailboxReadRightsResolverTest {
    private static final Username OWNER = Username.of("owner@domain.tld");
    private static final Username OUTSIDER = Username.of("outsider@domain.tld");

    @Test
    void shouldResolveOwnerWhenAclIsEmpty() {
        MailboxReadRightsResolver testee = new MailboxReadRightsResolver(new UnionMailboxACLResolver());

        // assume acl is disabled and we have MailboxACL.EMPTY returned
        List<Username> usersHavingReadRight = testee.usersHavingReadRight(OWNER, MailboxACL.EMPTY)
            .collectList()
            .block();

        assertThat(usersHavingReadRight).containsExactly(OWNER);
    }

    @Test
    void shouldResolveOwnerFromMailboxResolvedAclWhenAclIsEmpty() throws Exception {
        MailboxReadRightsResolver testee = new MailboxReadRightsResolver(new UnionMailboxACLResolver());
        MessageManager mailbox = mock(MessageManager.class);
        MailboxSession ownerSession = mock(MailboxSession.class);

        // assume acl is disabled and we have MailboxACL.EMPTY returned
        when(mailbox.getResolvedAcl(ownerSession)).thenReturn(MailboxACL.EMPTY);

        List<Username> usersHavingReadRight = testee.usersHavingReadRight(OWNER, mailbox, ownerSession)
            .collectList()
            .block();

        assertThat(usersHavingReadRight).containsExactly(OWNER);
    }

    @Test
    void shouldResolveOwnerReadRightWhenAclIsEmpty() {
        MailboxReadRightsResolver testee = new MailboxReadRightsResolver(new UnionMailboxACLResolver());

        // assume acl is disabled and we have MailboxACL.EMPTY returned
        Boolean ownerHasReadRight = testee.hasReadRight(OWNER, MailboxACL.EMPTY, OWNER)
            .block();
        Boolean outsiderHasReadRight = testee.hasReadRight(OWNER, MailboxACL.EMPTY, OUTSIDER)
            .block();

        assertThat(ownerHasReadRight).isTrue();
        assertThat(outsiderHasReadRight).isFalse();
    }
}
