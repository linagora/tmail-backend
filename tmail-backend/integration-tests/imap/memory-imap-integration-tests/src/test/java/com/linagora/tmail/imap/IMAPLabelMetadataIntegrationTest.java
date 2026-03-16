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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.net.MalformedServerReplyException;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.hash.Hashing;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.probe.JmapGuiceLabelProbe;
import com.linagora.tmail.james.jmap.label.LabelMetadataListener;
import com.linagora.tmail.james.jmap.model.Color;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelCreationRequest;

import scala.Option;
import scala.compat.java8.OptionConverters;

class IMAPLabelMetadataIntegrationTest {
    static final String DOMAIN = "domain.tld";
    static final Username ALICE = Username.of("alice@" + DOMAIN);
    static final String PASSWORD = "secret";
    static final String IMAP_HOST = "127.0.0.1";

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(JmapGuiceLabelProbe.class)))
        .build();

    @RegisterExtension
    TestIMAPClient imapClient = new TestIMAPClient();

    int imapPort;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(ALICE.asString(), PASSWORD);
        server.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.inbox(ALICE));

        imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    private String annotationPrefix(String keyword) {
        String hash = Hashing.murmur3_32_fixed().hashString(keyword, StandardCharsets.UTF_8).toString();
        return LabelMetadataListener.LABELS_ANNOTATION_PREFIX() + "/" + hash;
    }

    private Label createLabel(GuiceJamesServer server, String displayName, String color) {
        return server.getProbe(JmapGuiceLabelProbe.class)
            .addLabel(ALICE, new LabelCreationRequest(
                new DisplayName(displayName),
                Option.apply(color == null ? null : new Color(color)),
                OptionConverters.toScala(Optional.empty()),
                false));
    }

    @Test
    void getMetadataShouldReturnKeywordAnnotationAfterLabelCreated(GuiceJamesServer server) throws Exception {
        Label label = createLabel(server, "Work", "#FF0000");
        String prefix = annotationPrefix(label.keyword());

        assertThat(imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/labels"))
            .contains(prefix + "/keyword")
            .contains(label.keyword());
    }

    @Test
    void getMetadataShouldReturnDisplayNameAnnotationAfterLabelCreated(GuiceJamesServer server) throws Exception {
        Label label = createLabel(server, "Work", "#FF0000");
        String prefix = annotationPrefix(label.keyword());

        String actual = imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/labels");
        assertThat(actual).contains(prefix + "/displayname \"Work\"");
    }

    @Test
    void getMetadataShouldReturnColorAnnotationAfterLabelCreated(GuiceJamesServer server) throws Exception {
        Label label = createLabel(server, "Work", "#FF0000");
        String prefix = annotationPrefix(label.keyword());

        assertThat(imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/labels"))
            .contains(prefix + "/color")
            .contains("#FF0000");
    }

    @Test
    void getMetadataShouldNotReturnColorAnnotationWhenColorAbsent(GuiceJamesServer server) throws Exception {
        Label label = createLabel(server, "Work", null);
        String prefix = annotationPrefix(label.keyword());

        assertThat(imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/labels"))
            .doesNotContain(prefix + "/color");
    }

    @Test
    void getMetadataShouldReturnAnnotationsForMultipleLabels(GuiceJamesServer server) throws Exception {
        Label work = createLabel(server, "Work", "#FF0000");
        Label personal = createLabel(server, "Personal", "#0000FF");
        String workPrefix = annotationPrefix(work.keyword());
        String personalPrefix = annotationPrefix(personal.keyword());

        String response = imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/labels");
        assertThat(response).contains(workPrefix + "/displayname \"Work\"");
        assertThat(response).contains(personalPrefix + "/displayname \"Personal\"");
    }

    @Test
    void getMetadataShouldReturnEmptyAfterLabelDestroyed(GuiceJamesServer server) throws Exception {
        Label label = createLabel(server, "Work", "#FF0000");
        String prefix = annotationPrefix(label.keyword());

        server.getProbe(JmapGuiceLabelProbe.class).deleteLabel(ALICE, label.id());

        String response = imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/labels");

        assertThat(response)
            .doesNotContain(prefix + "/keyword")
            .doesNotContain(prefix + "/displayname")
            .doesNotContain(prefix + "/color");
    }

    @Test
    void setMetadataShouldBeRejectedOnLabelAnnotationPrefix(GuiceJamesServer server) throws Exception {
        assertThatThrownBy(() -> imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("SETMETADATA \"INBOX\" (/private/vendor/tmail/labels/abc123/displayname \"hacked\")"))
            .isInstanceOf(MalformedServerReplyException.class)
            .hasMessageContaining("NO SETMETADATA annotation is read-only: /private/vendor/tmail/labels/");
    }
}
