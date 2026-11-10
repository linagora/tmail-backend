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
package com.linagora.tmail.agent;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.time.Instant;

import jakarta.mail.Flags;
import org.apache.james.JamesServerExtension;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ListMailboxesToolTest extends AiToolsTestBase {

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = server();

    private static final Instant RECEIVED_AT = Instant.parse("2026-10-14T08:30:00Z");

    @Test
    void shouldListTheMailboxesOfTheUserWithTheirRoleAndCounts() throws Exception {
        MailboxId projectsId = mailboxProbe.createMailbox(MailboxPath.forUser(PAYING_USER, "Projects"));
        append(INBOX, email("Alice <alice@domain.tld>", "Meeting", "Shall we meet tomorrow?"), RECEIVED_AT);
        append(INBOX, email("Bob <bob@domain.tld>", "Report", "Here is the report"), RECEIVED_AT, new Flags(Flags.Flag.SEEN));

        String content = callTool(LIST_MAILBOXES, "{}");

        assertThatJson(content).isEqualTo("""
            {
              "mailboxes": [
                {"id": "%s", "name": "Archive", "role": "archive", "totalEmails": 0, "unreadEmails": 0},
                {"id": "%s", "name": "INBOX", "role": "inbox", "totalEmails": 2, "unreadEmails": 1},
                {"id": "%s", "name": "Projects", "role": null, "totalEmails": 0, "unreadEmails": 0}
              ]
            }""".formatted(archiveId.serialize(), inboxId.serialize(), projectsId.serialize()));
    }

    @Test
    void shouldIgnoreTheArgumentsTheLlmGives() {
        String content = callTool(LIST_MAILBOXES, "{\"role\": \"inbox\"}");

        assertThatJson(content).inPath("$.mailboxes[*].name").isArray().containsExactly("Archive", "INBOX");
    }
}
