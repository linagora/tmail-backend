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

import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.mailet.OBMLDAPMailingList.GroupResolutionResult;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.SearchResultEntry;

class OBMLDAPMailingListTest {
    private static final String LIST_DN = "cn=group-canut,ou=groups,dc=linagora.com,dc=lng";

    private OBMLDAPMailingList testee;
    private FakeMailContext mailetContext;

    @BeforeEach
    void setUp() throws Exception {
        mailetContext = FakeMailContext.defaultContext();
        testee = new OBMLDAPMailingList((LDAPConnectionPool) null, MailingListConfiguration.EMPTY);
        testee.init(FakeMailetConfig.builder()
            .mailetName("OBMLDAPMailingList")
            .setProperty("baseDN", "ou=groups,dc=linagora.com,dc=lng")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .mailetContext(mailetContext)
            .build());
    }

    private static FakeMail nullSenderMailTo(MailAddress recipient) throws Exception {
        return FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .recipient(recipient)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("test")
                .setText("content")
                .build())
            .build();
    }

    @Test
    void shouldRejectNullSenderWhenPrivateList() throws Exception {
        MailAddress listAddress = new MailAddress("group-canut@linagora.com");
        GroupResolutionResult privateList = new GroupResolutionResult(listAddress, false,
            ImmutableList.of(new MailAddress("member@linagora.com")));
        FakeMail mail = nullSenderMailTo(listAddress);

        testee.listToMailTransformation(MaybeSender.nullSender(), listAddress, privateList).transform(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getRecipients()).containsOnly(listAddress);
    }

    @Test
    void shouldAcceptNullSenderWhenPublicList() throws Exception {
        MailAddress listAddress = new MailAddress("group-canut@linagora.com");
        MailAddress member = new MailAddress("member@linagora.com");
        GroupResolutionResult publicList = new GroupResolutionResult(listAddress, true, ImmutableList.of(member));
        FakeMail mail = nullSenderMailTo(listAddress);

        testee.listToMailTransformation(MaybeSender.nullSender(), listAddress, publicList).transform(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isNotEqualTo("rejectedSender");
        assertThat(sentMail.getRecipients()).containsOnly(member);
    }

    private static SearchResultEntry entry(Attribute... attributes) {
        return new SearchResultEntry(LIST_DN, attributes);
    }

    @Test
    void shouldResolveExternalContactEmailAsMember() throws Exception {
        SearchResultEntry entry = entry(
            new Attribute("mail", "group-canut@linagora.com"),
            new Attribute("mailAccess", "PERMIT"),
            new Attribute("externalContactEmail", "ben@cozycloud.cc"));

        Optional<GroupResolutionResult> result = testee.asGroupResolutionResult(entry);

        assertThat(result).isPresent();
        assertThat(result.get().members()).containsExactly(new MailAddress("ben@cozycloud.cc"));
    }

    @Test
    void shouldCombineMailBoxAndExternalContactEmailMembers() throws Exception {
        SearchResultEntry entry = entry(
            new Attribute("mail", "group-canut@linagora.com"),
            new Attribute("mailAccess", "PERMIT"),
            new Attribute("mailBox", "alice@linagora.com", "bob@linagora.com"),
            new Attribute("externalContactEmail", "ben@cozycloud.cc"));

        Optional<GroupResolutionResult> result = testee.asGroupResolutionResult(entry);

        assertThat(result).isPresent();
        assertThat(result.get().members()).containsExactlyInAnyOrder(
            new MailAddress("alice@linagora.com"),
            new MailAddress("bob@linagora.com"),
            new MailAddress("ben@cozycloud.cc"));
    }

    @Test
    void shouldSupportMultivaluedExternalContactEmail() throws Exception {
        SearchResultEntry entry = entry(
            new Attribute("mail", "group-canut@linagora.com"),
            new Attribute("mailAccess", "PERMIT"),
            new Attribute("externalContactEmail", "ben@cozycloud.cc", "alice@external.com"));

        Optional<GroupResolutionResult> result = testee.asGroupResolutionResult(entry);

        assertThat(result).isPresent();
        assertThat(result.get().members()).containsExactlyInAnyOrder(
            new MailAddress("ben@cozycloud.cc"),
            new MailAddress("alice@external.com"));
    }

    @Test
    void shouldDeduplicateMembersSharedByBothAttributes() throws Exception {
        SearchResultEntry entry = entry(
            new Attribute("mail", "group-canut@linagora.com"),
            new Attribute("mailAccess", "PERMIT"),
            new Attribute("mailBox", "ben@cozycloud.cc"),
            new Attribute("externalContactEmail", "ben@cozycloud.cc"));

        Optional<GroupResolutionResult> result = testee.asGroupResolutionResult(entry);

        assertThat(result).isPresent();
        assertThat(result.get().members()).containsExactly(new MailAddress("ben@cozycloud.cc"));
    }

    @Test
    void shouldResolveEmptyMembersWhenNoMailBoxNorExternalContactEmail() throws Exception {
        SearchResultEntry entry = entry(
            new Attribute("mail", "group-canut@linagora.com"),
            new Attribute("mailAccess", "PERMIT"));

        Optional<GroupResolutionResult> result = testee.asGroupResolutionResult(entry);

        assertThat(result).isPresent();
        assertThat(result.get().members()).isEmpty();
    }
}
