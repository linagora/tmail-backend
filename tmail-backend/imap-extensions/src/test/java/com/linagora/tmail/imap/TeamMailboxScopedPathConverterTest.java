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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imapserver.netty.NettyImapSession;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;

public class TeamMailboxScopedPathConverterTest {
    public static final boolean RELATIVE = true;
    static final String DOMAIN = "domain.tld";
    static final Username MEMBER = Username.of("member@" + DOMAIN);
    static final Username TEAM_MAILBOX_OWNER = Username.of("team-mailbox@" + DOMAIN);
    static final TeamMailbox MARKETING = TeamMailbox.apply(Domain.of(DOMAIN),
        TeamMailboxName.fromString("marketing").toOption().get());

    MailboxSession mailboxSession;
    ImapSession imapSession;
    PathConverter pathConverter;

    @BeforeEach
    void setUp() {
        mailboxSession = MailboxSessionUtil.create(MEMBER);
        TeamMailboxScope.scopeTo(mailboxSession, MARKETING);
        imapSession = new NettyImapSession(null, null, false, null);
        imapSession.setMailboxSession(mailboxSession);
        pathConverter = new TMailPathConverterFactory().forSession(mailboxSession);
    }

    @Test
    void factoryShouldReturnScopedConverterWhenScopeIsPresent() {
        assertThat(pathConverter).isExactlyInstanceOf(TeamMailboxScopedPathConverter.class);
    }

    @Test
    void buildFullPathShouldMapInboxToTeamMailboxInbox() {
        assertThat(pathConverter.buildFullPath("INBOX"))
            .isEqualTo(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.INBOX"));
    }

    @Test
    void buildFullPathShouldCanonicalizeInboxCase() {
        assertThat(pathConverter.buildFullPath("inbox"))
            .isEqualTo(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.INBOX"));
    }

    @Test
    void buildFullPathShouldNotCanonicalizeOtherMailboxCase() {
        assertThat(pathConverter.buildFullPath("sent"))
            .isEqualTo(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.sent"));
    }

    @Test
    void buildFullPathShouldMapRelativeSubFolderToTeamMailboxSubTree() {
        assertThat(pathConverter.buildFullPath("Sent.2024"))
            .isEqualTo(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.Sent.2024"));
    }

    @Test
    void buildFullPathShouldStripPrivateNamespace() {
        assertThat(pathConverter.buildFullPath("#private.INBOX"))
            .isEqualTo(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.INBOX"));
    }

    @Test
    void buildFullPathShouldReturnTeamRootWhenEmpty() {
        assertThat(pathConverter.buildFullPath(""))
            .isEqualTo(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing"));
    }

    @Test
    void buildFullPathShouldKeepAbsoluteTeamMailboxPath() {
        assertThat(pathConverter.buildFullPath("#TeamMailbox.sale.INBOX"))
            .isEqualTo(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "sale.INBOX"));
    }

    @Test
    void mailboxNameShouldEchoScopedFolderRelativeToRoot() {
        assertThat(pathConverter.mailboxName(RELATIVE,
                new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.INBOX"), mailboxSession))
            .contains("INBOX");
    }

    @Test
    void mailboxNameShouldEchoScopedSubFolderRelativeToRoot() {
        assertThat(pathConverter.mailboxName(RELATIVE,
                new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.Sent.2024"), mailboxSession))
            .contains("Sent.2024");
    }

    @Test
    void mailboxNameShouldFallBackForOtherTeamMailbox() {
        assertThat(pathConverter.mailboxName(RELATIVE,
                new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "sale.INBOX"), mailboxSession))
            .contains("#TeamMailbox.sale.INBOX");
    }

    @Test
    void mailboxQueryShouldMatchScopedFoldersWhenNoReferenceName() {
        MailboxQuery query = pathConverter.mailboxQuery("", "*", imapSession);

        assertThat(query.isPathMatch(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.Sent")))
            .isTrue();
    }

    @Test
    void mailboxQueryShouldMatchReferenceNameItself() {
        MailboxQuery query = pathConverter.mailboxQuery("Sent", "*", imapSession);

        assertThat(query.isPathMatch(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.Sent")))
            .isTrue();
    }

    @Test
    void mailboxQueryShouldMatchChildrenOfReferenceName() {
        MailboxQuery query = pathConverter.mailboxQuery("Sent", "*", imapSession);

        assertThat(query.isPathMatch(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.Sent.2024")))
            .isTrue();
    }

    @Test
    void mailboxQueryShouldNotMatchOtherScopedFoldersThanReferenceName() {
        MailboxQuery query = pathConverter.mailboxQuery("Sent", "*", imapSession);

        assertThat(query.isPathMatch(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.INBOX")))
            .isFalse();
    }

    @Test
    void mailboxQueryShouldDecodeModifiedUtf7ReferenceName() {
        MailboxQuery query = pathConverter.mailboxQuery("&AOk-t&AOk-", "*", imapSession);

        assertThat(query.isPathMatch(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "marketing.été.2024")))
            .isTrue();
    }

    @Test
    void absolutePathShouldKeepFullDomainBehaviourWhenFullDomainEnabled() {
        try {
            TMailPathConverterFactory.IS_FULL_DOMAIN_ENABLED = true;
            PathConverter fullDomainPathConverter = new TMailPathConverterFactory().forSession(mailboxSession);

            assertThat(fullDomainPathConverter.buildFullPath("#TeamMailbox.sale@domain__tld.INBOX"))
                .isEqualTo(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "sale.INBOX"));
        } finally {
            TMailPathConverterFactory.IS_FULL_DOMAIN_ENABLED = false;
        }
    }

    @Test
    void mailboxNameShouldKeepFullDomainBehaviourForOtherTeamMailboxWhenFullDomainEnabled() {
        try {
            TMailPathConverterFactory.IS_FULL_DOMAIN_ENABLED = true;
            PathConverter fullDomainPathConverter = new TMailPathConverterFactory().forSession(mailboxSession);

            assertThat(fullDomainPathConverter.mailboxName(RELATIVE,
                    new MailboxPath("#TeamMailbox", TEAM_MAILBOX_OWNER, "sale.INBOX"), mailboxSession))
                .contains("#TeamMailbox.sale@domain__tld.INBOX");
        } finally {
            TMailPathConverterFactory.IS_FULL_DOMAIN_ENABLED = false;
        }
    }
}
