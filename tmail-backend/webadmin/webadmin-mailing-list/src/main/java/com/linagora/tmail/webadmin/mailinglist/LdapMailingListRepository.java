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

package com.linagora.tmail.webadmin.mailinglist;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.mailet.MailingListConfiguration;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

/**
 * <p>Read only view of the mailing lists stored in LDAP, backing the {@code /mailingLists} webadmin routes.</p>
 *
 * <p>Both the default groupOfNames schema (members resolved from the {@code member} / {@code owner} DN attributes) and
 * the OBM schema (members resolved from the {@code mailBox} / {@code externalContactEmail} mail attributes) are
 * supported, depending on the {@code obm.compatibility} flag of the {@link MailingListConfiguration}.</p>
 */
public class LdapMailingListRepository implements MailingListRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(LdapMailingListRepository.class);
    private static final String OBM_GROUP_OBJECT_CLASS = "obmGroup";
    private static final String GROUP_OF_NAMES_OBJECT_CLASS = "groupofnames";
    private static final String BUSINESS_CATEGORY_ATTRIBUTE = "businessCategory";
    private static final String MEMBER_ATTRIBUTE = "member";
    private static final String OWNER_ATTRIBUTE = "owner";
    private static final String MAIL_BOX_ATTRIBUTE = "mailBox";
    private static final String EXTERNAL_CONTACT_EMAIL_ATTRIBUTE = "externalContactEmail";

    private final LDAPConnectionPool ldapConnectionPool;
    private final LdapRepositoryConfiguration ldapConfiguration;
    private final String baseDN;
    private final String mailAttributeForGroups;
    private final boolean obmCompatibility;
    private final Filter objectClassFilter;

    public LdapMailingListRepository(LDAPConnectionPool ldapConnectionPool,
                                     LdapRepositoryConfiguration ldapConfiguration,
                                     MailingListConfiguration mailingListConfiguration) {
        this.ldapConnectionPool = ldapConnectionPool;
        this.ldapConfiguration = ldapConfiguration;
        this.baseDN = mailingListConfiguration.baseDN()
            .orElseThrow(MailingListsNotConfiguredException::new);
        this.mailAttributeForGroups = mailingListConfiguration.mailAttributeForGroups();
        this.obmCompatibility = mailingListConfiguration.obmCompatibility();
        this.objectClassFilter = Filter.createEqualityFilter("objectClass",
            obmCompatibility ? OBM_GROUP_OBJECT_CLASS : GROUP_OF_NAMES_OBJECT_CLASS);
    }

    @Override
    public List<MailAddress> list() {
        return search(objectClassFilter, mailAttributeForGroups).stream()
            .map(entry -> entry.getAttributeValue(mailAttributeForGroups))
            .filter(Objects::nonNull)
            .map(Throwing.function(MailAddress::new))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public List<MailAddress> list(Domain domain) {
        return list().stream()
            .filter(address -> address.getDomain().equals(domain))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Optional<MailingList> get(MailAddress address) {
        Filter filter = Filter.createANDFilter(objectClassFilter,
            Filter.createEqualityFilter(mailAttributeForGroups, address.asString()));
        return search(filter,
            mailAttributeForGroups, BUSINESS_CATEGORY_ATTRIBUTE, MEMBER_ATTRIBUTE, OWNER_ATTRIBUTE,
            MAIL_BOX_ATTRIBUTE, EXTERNAL_CONTACT_EMAIL_ATTRIBUTE)
            .stream()
            .findFirst()
            .map(Throwing.function(this::toMailingList));
    }

    private MailingList toMailingList(SearchResultEntry entry) throws Exception {
        Optional<String> businessCategory = Optional.ofNullable(entry.getAttributeValue(BUSINESS_CATEGORY_ATTRIBUTE));
        List<MailAddress> members;
        List<MailAddress> owners;
        if (obmCompatibility) {
            members = Stream.concat(
                    mailAttributes(entry, MAIL_BOX_ATTRIBUTE),
                    mailAttributes(entry, EXTERNAL_CONTACT_EMAIL_ATTRIBUTE))
                .distinct()
                .collect(ImmutableList.toImmutableList());
            owners = ImmutableList.of();
        } else {
            members = resolveDnAttribute(entry, MEMBER_ATTRIBUTE);
            owners = resolveDnAttribute(entry, OWNER_ATTRIBUTE);
        }
        return new MailingList(new MailAddress(entry.getAttributeValue(mailAttributeForGroups)),
            businessCategory, members, owners);
    }

    private Stream<MailAddress> mailAttributes(SearchResultEntry entry, String attribute) {
        if (!entry.hasAttribute(attribute)) {
            return Stream.empty();
        }
        return Arrays.stream(entry.getAttribute(attribute).getValues())
            .map(Throwing.function(MailAddress::new));
    }

    private List<MailAddress> resolveDnAttribute(SearchResultEntry entry, String attribute) {
        if (!entry.hasAttribute(attribute)) {
            return ImmutableList.of();
        }
        Set<String> visitedDns = new HashSet<>();
        visitedDns.add(entry.getDN());
        return Arrays.stream(entry.getAttribute(attribute).getValues())
            .flatMap(dn -> resolveMail(dn, visitedDns))
            .distinct()
            .collect(ImmutableList.toImmutableList());
    }

    /**
     * Resolves a member/owner DN into mail addresses. When the DN points to another group (i.e. it holds a
     * {@code member} attribute) it is expanded recursively, mirroring the production {@code LDAPMailingList} mailet.
     * The {@code visitedDns} set guards against loops introduced by recursive group definitions.
     */
    private Stream<MailAddress> resolveMail(String dn, Set<String> visitedDns) {
        if (!visitedDns.add(dn)) {
            LOGGER.warn("Recursive LDAP group definition creates a loop for dn {}. Please review LDAP data", dn);
            return Stream.empty();
        }
        try {
            SearchResultEntry entry = ldapConnectionPool.getEntry(dn, ldapConfiguration.getUserIdAttribute(), MEMBER_ATTRIBUTE);
            if (entry == null) {
                return Stream.empty();
            }
            if (entry.hasAttribute(MEMBER_ATTRIBUTE)) {
                return Arrays.stream(entry.getAttribute(MEMBER_ATTRIBUTE).getValues())
                    .flatMap(nestedDn -> resolveMail(nestedDn, visitedDns));
            }
            return Optional.ofNullable(entry.getAttributeValue(ldapConfiguration.getUserIdAttribute()))
                .map(Throwing.function(MailAddress::new))
                .stream();
        } catch (LDAPException e) {
            throw new RuntimeException(e);
        }
    }

    private List<SearchResultEntry> search(Filter filter, String... attributes) {
        try {
            SearchResult searchResult = ldapConnectionPool.search(baseDN, SearchScope.SUB, filter, attributes);
            return ImmutableList.copyOf(searchResult.getSearchEntries());
        } catch (LDAPException e) {
            if (e.getResultCode().equals(ResultCode.NO_SUCH_OBJECT)) {
                return ImmutableList.of();
            }
            throw new RuntimeException(e);
        }
    }
}
