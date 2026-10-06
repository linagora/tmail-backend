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

package com.linagora.tmail.team.events;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;

import com.linagora.tmail.team.TeamMailbox;

public record TeamMailboxEvent(TeamMailbox teamMailbox,
                               Direction direction,
                               MailboxPath mailboxPath,
                               MailboxId mailboxId,
                               MessageId messageId,
                               Optional<String> subject,
                               Optional<List<EmailAddress>> from,
                               Optional<List<EmailAddress>> to,
                               Optional<List<EmailAddress>> cc,
                               Optional<List<EmailAddress>> bcc,
                               Optional<Instant> date,
                               Instant timestamp) {

    public enum Direction {
        RECEIVED("received"),
        SENT("sent");

        private final String value;

        Direction(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }
    }

    public record EmailAddress(Optional<String> name, String email) {
    }
}
