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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

/**
 * Matcher matching mailing lists defined by the LDAP.
 *
 * Useful for integrating LDAP lists with Recipient Rewrite Tables: it can be used to re-process lists generated as
 * an output of RRTs.
 *
 * Matcher argument needs to be in the form baseDN#groupObjectClass#mailAttribute, optionally followed by
 * #extraFilter: an LDAP filter the group must also match. Use the extraFilter of the LDAPMailingList mailet, so that
 * groups the mailet does not expand are not sent back to it, which would loop.
 *
 * Sample usage:
 *
 * <mailet match="com.linagora.tmail.mailet.IsALDAPMailingList=ou=lists,dc=tmail,dc=com#groupofnames#description" class="ToProcessor">
 *   <processor>transport</processor>
 * </mailet>
 *
 * <mailet match="com.linagora.tmail.mailet.IsALDAPMailingList=ou=groups,dc=tmail,dc=com#groupofnames#mail#(businessCategory=*)" class="ToProcessor">
 *   <processor>transport</processor>
 * </mailet>
 *
 */
public class IsALDAPMailingList extends GenericMatcher {
    private final LDAPConnectionPool ldapConnectionPool;
    private final Optional<Filter> userExtraFilter;
    private String baseDN;
    private String groupObjectClass;
    private String mailAttribute;
    private Optional<Filter> groupExtraFilter = Optional.empty();

    @Inject
    public IsALDAPMailingList(LDAPConnectionPool ldapConnectionPool, LdapRepositoryConfiguration configuration) {
        this.ldapConnectionPool = ldapConnectionPool;
        this.userExtraFilter = Optional.ofNullable(configuration.getFilter())
            .map(Throwing.function(Filter::create).sneakyThrow());
    }

    @VisibleForTesting
    public IsALDAPMailingList(LdapRepositoryConfiguration configuration) throws LDAPException {
        this(new LDAPConnectionFactory(configuration).getLdapConnectionPool(), configuration);
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        return mail.getRecipients()
            .stream()
            .filter(Throwing.predicate(rcpt -> resolveListDN(rcpt).isPresent()))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public void init() throws MessagingException {
        // The extra filter is last and kept whole: a filter value may contain '#'
        List<String> parts = Splitter.on('#').limit(4).splitToList(getCondition());
        Preconditions.checkState(parts.size() >= 3, "Must match the 'baseDN#groupObjectClass#mailAttribute[#extraFilter]");

        baseDN = parts.get(0);
        groupObjectClass = parts.get(1);
        mailAttribute = parts.get(2);
        groupExtraFilter = parts.stream()
            .skip(3)
            .findFirst()
            .map(Throwing.function(Filter::create).sneakyThrow());
    }

    private Optional<SearchResultEntry> resolveListDN(MailAddress rcpt) throws LDAPSearchException {
        try {
            SearchResult searchResult = ldapConnectionPool.search(baseDN,
                SearchScope.SUB,
                createFilter(rcpt.asString(), mailAttribute));

            return searchResult.getSearchEntries().stream().findFirst();
        } catch (LDAPException e) {
            if (e.getResultCode().equals(ResultCode.NO_SUCH_OBJECT)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private Filter createFilter(String retrievalName, String ldapUserRetrievalAttribute) {
        Filter specificUserFilter = Filter.createEqualityFilter(ldapUserRetrievalAttribute, retrievalName);
        Filter objectClassFilter = Filter.createEqualityFilter("objectClass", groupObjectClass);
        return Filter.createANDFilter(Stream.of(Optional.of(objectClassFilter), Optional.of(specificUserFilter), userExtraFilter, groupExtraFilter)
            .flatMap(Optional::stream)
            .toList());
    }
}
