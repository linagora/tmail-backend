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
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.mailet.OBMLDAPMailingList.GroupResolutionResult;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.SearchResultEntry;

class OBMLDAPMailingListTest {
    private static final String LIST_DN = "cn=group-canut,ou=groups,dc=linagora.com,dc=lng";

    private OBMLDAPMailingList testee;

    @BeforeEach
    void setUp() throws Exception {
        testee = new OBMLDAPMailingList((LDAPConnectionPool) null);
        testee.init(FakeMailetConfig.builder()
            .mailetName("OBMLDAPMailingList")
            .setProperty("baseDN", "ou=groups,dc=linagora.com,dc=lng")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .mailetContext(FakeMailContext.defaultContext())
            .build());
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
