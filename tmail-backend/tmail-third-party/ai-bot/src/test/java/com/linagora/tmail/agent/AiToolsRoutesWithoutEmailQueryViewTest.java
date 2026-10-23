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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AiToolsRoutesWithoutEmailQueryViewTest extends AiToolsTestBase {

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = server(binder -> binder.bind(JMAPConfiguration.class)
        .toInstance(JMAPConfiguration.builder()
            .enable()
            .randomPort()
            .disableEmailQueryView()
            .build()));

    @Test
    void listEmailsShouldUseTheSearchIndexWhenTheViewIsDisabled() throws Exception {
        MessageId monday = append(INBOX, email("alice@domain.tld", "Monday", "Body"),
            Instant.parse("2026-10-12T09:00:00Z"));
        MessageId wednesday = append(INBOX, email("alice@domain.tld", "Wednesday", "Body"),
            Instant.parse("2026-10-14T09:00:00Z"));
        MessageId tuesday = append(INBOX, email("alice@domain.tld", "Tuesday", "Body"),
            Instant.parse("2026-10-13T09:00:00Z"));

        assertThat(emailIds(callTool(LIST_EMAILS, "{}")))
            .containsExactly(wednesday.serialize(), tuesday.serialize(), monday.serialize());
        assertThat(emailIds(callTool(LIST_EMAILS, "{\"page\": 2, \"limit\": 2}")))
            .containsExactly(monday.serialize());
    }
}
