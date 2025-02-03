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

import org.apache.james.core.Username;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imapserver.netty.NettyImapSession;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class TMailPathConverterFactoryTest {
    public static final ImapSession imapSession = new NettyImapSession(
            null, null, false, false,
            false, null, Optional.empty()
    );
    public static final MailboxSession mailboxSession = MailboxSessionUtil.create(Username.of("username"));

    @BeforeAll
    public static void setUp() {
        imapSession.setMailboxSession(mailboxSession);
    }

    @Test
    public void forMailboxSessionCreatesTMailPathConverterByDefault() {
        assertThat(new TMailPathConverterFactory().forSession(mailboxSession))
                .isExactlyInstanceOf(TMailPathConverter.class);
    }

    @Test
    public void forMailboxSessionCreatesFullDomainTMailPathConverterWhenFullDomainEnabled() {
        try {
            TMailPathConverterFactory.IS_FULL_DOMAIN_ENABLED = true;

            assertThat(new TMailPathConverterFactory().forSession(mailboxSession))
                    .isExactlyInstanceOf(TMailFullDomainPathConverter.class);
        } finally {
            TMailPathConverterFactory.IS_FULL_DOMAIN_ENABLED = false;
        }
    }

    @Test
    public void forImapSessionCreatesTMailPathConverterByDefault() {
        assertThat(new TMailPathConverterFactory().forSession(imapSession))
                .isExactlyInstanceOf(TMailPathConverter.class);
    }

    @Test
    public void forImapSessionCreatesFullDomainTMailPathConverterWhenFullDomainEnabled() {
        try {
            TMailPathConverterFactory.IS_FULL_DOMAIN_ENABLED = true;

            assertThat(new TMailPathConverterFactory().forSession(imapSession))
                    .isExactlyInstanceOf(TMailFullDomainPathConverter.class);
        } finally {
            TMailPathConverterFactory.IS_FULL_DOMAIN_ENABLED = false;
        }
    }
}
