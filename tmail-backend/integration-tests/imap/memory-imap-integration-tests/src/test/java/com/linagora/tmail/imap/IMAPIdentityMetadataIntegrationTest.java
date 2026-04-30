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

import java.nio.charset.StandardCharsets;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Identity;
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
import com.linagora.tmail.james.common.probe.JmapGuiceIdentityProbe;
import com.linagora.tmail.james.jmap.identity.IdentityMetadataListener;

class IMAPIdentityMetadataIntegrationTest {
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
                .addBinding().to(JmapGuiceIdentityProbe.class)))
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

    private String annotationPrefix(Identity identity) {
        String hash = Hashing.murmur3_32_fixed()
            .hashString(identity.id().id().toString(), StandardCharsets.UTF_8)
            .toString();
        return IdentityMetadataListener.IDENTITIES_ANNOTATION_PREFIX() + "/" + hash;
    }

    @Test
    void getMetadataShouldReturnDisplayNameAnnotationAfterIdentityCreated(GuiceJamesServer server) throws Exception {
        Identity identity = server.getProbe(JmapGuiceIdentityProbe.class).addIdentity(ALICE, "Work identity");
        String prefix = annotationPrefix(identity);

        assertThat(imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/identities"))
            .contains(prefix + "/displayname \"Work identity\"");
    }

    @Test
    void getMetadataShouldReturnHtmlSignatureAnnotationAfterIdentityCreated(GuiceJamesServer server) throws Exception {
        Identity identity = server.getProbe(JmapGuiceIdentityProbe.class).addIdentity(ALICE, "Work identity");
        String prefix = annotationPrefix(identity);

        assertThat(imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/identities"))
            .contains(prefix + "/html");
    }

    @Test
    void getMetadataShouldReturnReplyToAnnotationWhenProvided(GuiceJamesServer server) throws Exception {
        Identity identity = server.getProbe(JmapGuiceIdentityProbe.class)
            .addIdentityWithReplyTo(ALICE, "Work identity", "boss@domain.tld");
        String prefix = annotationPrefix(identity);

        assertThat(imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/identities"))
            .contains(prefix + "/replyto");
    }

    @Test
    void getMetadataShouldNotReturnReplyToAnnotationWhenAbsent(GuiceJamesServer server) throws Exception {
        Identity identity = server.getProbe(JmapGuiceIdentityProbe.class).addIdentity(ALICE, "Work identity");
        String prefix = annotationPrefix(identity);

        assertThat(imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/identities"))
            .doesNotContain(prefix + "/replyto");
    }

    @Test
    void getMetadataShouldReturnAnnotationsForMultipleIdentities(GuiceJamesServer server) throws Exception {
        Identity work = server.getProbe(JmapGuiceIdentityProbe.class).addIdentity(ALICE, "Work identity");
        Identity personal = server.getProbe(JmapGuiceIdentityProbe.class).addIdentity(ALICE, "Personal identity");
        String workPrefix = annotationPrefix(work);
        String personalPrefix = annotationPrefix(personal);

        String response = imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/identities");

        assertThat(response).contains(workPrefix + "/displayname \"Work identity\"");
        assertThat(response).contains(personalPrefix + "/displayname \"Personal identity\"");
    }

    @Test
    void getMetadataShouldReturnEmptyAfterIdentityDeleted(GuiceJamesServer server) throws Exception {
        Identity identity = server.getProbe(JmapGuiceIdentityProbe.class).addIdentity(ALICE, "Work identity");
        String prefix = annotationPrefix(identity);

        server.getProbe(JmapGuiceIdentityProbe.class).deleteIdentity(ALICE, identity.id());

        String response = imapClient.connect(IMAP_HOST, imapPort)
            .login(ALICE, PASSWORD)
            .sendCommand("GETMETADATA \"INBOX\" (DEPTH infinity) /private/vendor/tmail/identities");

        assertThat(response)
            .doesNotContain(prefix + "/displayname")
            .doesNotContain(prefix + "/html")
            .doesNotContain(prefix + "/id");
    }
}
