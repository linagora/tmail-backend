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

package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Domain;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxCallbackNoop;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

class TMailSubAdressingTest {

    private static final Domain DOMAIN = Domain.of("linagora.com");
    private static final TeamMailbox TEAM_MAILBOX = OptionConverters.toJava(TeamMailbox.fromJava(DOMAIN, "marketing")).orElseThrow();
    private static final String SENDER = "sender@linagora.com";
    private static final String TARGET_FOLDER = "abc";
    private static final String TARGET_NONEXISTENT = "nonexistent";
    private static final String EXPECTED_FOLDER_NAME = "abc";
    private static final AttributeName RECIPIENT_ATTRIBUTE = AttributeName.of("DeliveryPaths_marketing@linagora.com");

    private InMemoryMailboxManager mailboxManager;
    private TMailSubAdressing testee;
    private MailboxSession ownerSession;
    private MailboxId mailboxId;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(null);

        StoreSubscriptionManager subscriptionManager = new StoreSubscriptionManager(
            resources.getMailboxManager().getMapperFactory(),
            resources.getMailboxManager().getMapperFactory(),
            resources.getMailboxManager().getEventBus());

        TeamMailboxRepository teamMailboxRepository = new TeamMailboxRepositoryImpl(
            mailboxManager, subscriptionManager, mailboxManager.getMapperFactory(),
            java.util.Set.of(new TeamMailboxCallbackNoop()));

        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
        ownerSession = mailboxManager.createSystemSession(TEAM_MAILBOX.owner());
        mailboxId = mailboxManager.createMailbox(TEAM_MAILBOX.mailboxPath(TARGET_FOLDER), mailboxManager.createSystemSession(TEAM_MAILBOX.owner())).get();

        testee = new TMailSubAdressing(usersRepository, mailboxManager);
        testee.init(FakeMailetConfig.builder().build());
    }

    @Test
    void shouldNotAddStorageDirectiveWhenTeamMailboxFolderDoesNotExist() throws Exception {
        givePostRightForKey(MailboxACL.ANYONE_KEY);

        Mail mail = mailBuilder(TARGET_NONEXISTENT).sender(SENDER).build();
        testee.service(mail);

        assertThat(mail.attributes().map(this::unbox))
            .doesNotContain(Pair.of(RECIPIENT_ATTRIBUTE, "marketing." + TARGET_NONEXISTENT));
    }

    @Test
    void shouldNotAddStorageDirectiveWhenSenderHasNoPostRight() throws Exception {
        Mail mail = mailBuilder(TARGET_FOLDER).sender(SENDER).build();
        testee.service(mail);

        assertThat(mail.attributes().map(this::unbox))
            .doesNotContain(Pair.of(RECIPIENT_ATTRIBUTE, EXPECTED_FOLDER_NAME));
    }

    @Test
    void shouldAddStorageDirectiveWhenAnyoneHasPostRight() throws Exception {
        givePostRightForKey(MailboxACL.ANYONE_KEY);

        Mail mail = mailBuilder(TARGET_FOLDER).sender(SENDER).build();
        testee.service(mail);

        assertThat(mail.attributes().map(this::unbox))
            .containsOnly(Pair.of(RECIPIENT_ATTRIBUTE, EXPECTED_FOLDER_NAME));
    }

    @Test
    void shouldAddStorageDirectiveWhenAnyoneHasPostRightAndSenderIsUnknown() throws Exception {
        givePostRightForKey(MailboxACL.ANYONE_KEY);

        Mail mail = mailBuilder(TARGET_FOLDER).build();
        testee.service(mail);

        assertThat(mail.attributes().map(this::unbox))
            .containsOnly(Pair.of(RECIPIENT_ATTRIBUTE, EXPECTED_FOLDER_NAME));
    }

    @Test
    void shouldAddStorageDirectiveMatchingCaseInsensitively() throws Exception {
        givePostRightForKey(MailboxACL.ANYONE_KEY);

        Mail mail = mailBuilder("aBc").sender(SENDER).build();
        testee.service(mail);

        assertThat(mail.attributes().map(this::unbox))
            .containsOnly(Pair.of(RECIPIENT_ATTRIBUTE, EXPECTED_FOLDER_NAME));
    }

    private FakeMail.Builder mailBuilder(String targetFolder) throws MessagingException {
        return FakeMail.builder()
            .name("name")
            .recipient("marketing+" + targetFolder + "@linagora.com");
    }

    private void givePostRightForKey(MailboxACL.EntryKey key) throws MailboxException {
        MailboxACL.ACLCommand command = MailboxACL.command()
            .key(key)
            .rights(MailboxACL.Right.Post)
            .asAddition();

        mailboxManager.applyRightsCommand(mailboxId, command, ownerSession);
    }

    Pair<AttributeName, String> unbox(Attribute attribute) {
        Collection<AttributeValue> collection = (Collection<AttributeValue>) attribute.getValue().getValue();
        return Pair.of(attribute.getName(), (String) collection.stream().findFirst().get().getValue());
    }
}
