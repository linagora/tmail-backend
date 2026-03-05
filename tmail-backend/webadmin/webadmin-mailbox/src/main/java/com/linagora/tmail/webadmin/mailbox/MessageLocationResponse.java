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

package com.linagora.tmail.webadmin.mailbox;

import java.util.List;

public class MessageLocationResponse {

    public static class MailboxEntry {
        private final String mailboxId;
        private final String mailboxPath;

        public MailboxEntry(String mailboxId, String mailboxPath) {
            this.mailboxId = mailboxId;
            this.mailboxPath = mailboxPath;
        }

        public String getMailboxId() {
            return mailboxId;
        }

        public String getMailboxPath() {
            return mailboxPath;
        }
    }

    private final List<MailboxEntry> mailboxes;

    public MessageLocationResponse(List<MailboxEntry> mailboxes) {
        this.mailboxes = mailboxes;
    }

    public List<MailboxEntry> getMailboxes() {
        return mailboxes;
    }
}
