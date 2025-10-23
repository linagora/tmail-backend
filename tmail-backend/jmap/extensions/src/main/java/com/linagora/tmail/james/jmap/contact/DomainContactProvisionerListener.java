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

package com.linagora.tmail.james.jmap.contact;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents.MailboxAdded;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

import reactor.core.publisher.Mono;

public class DomainContactProvisionerListener implements EventListener.ReactiveGroupEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainContactProvisionerListener.class);
    private static final String IGNORED_DOMAINS_PROPERTY = "ignoredDomains";
    private static final String FIRSTNAME_PROPERTY = "firstnameAttribute";
    private static final String SURNAME_PROPERTY = "surnameAttribute";

    public static class DomainContactProvisionerListenerGroup extends Group {

    }

    static final Group GROUP = new DomainContactProvisionerListenerGroup();

    private final EmailAddressContactSearchEngine contactSearchEngine;
    private final List<Domain> ignoredDomains;
    private final LDAPConnectionPool ldapConnectionPool;
    private final LdapRepositoryConfiguration ldapConfiguration;
    private final Filter objectClassFilter;
    private final Optional<Filter> userExtraFilter;
    private final String firstnameAttribute;
    private final String surnameAttribute;
    private final String usernameAttribute;

    @Inject
    public DomainContactProvisionerListener(EmailAddressContactSearchEngine contactSearchEngine,
                                            LDAPConnectionPool ldapConnectionPool,
                                            LdapRepositoryConfiguration ldapConfiguration,
                                            HierarchicalConfiguration<ImmutableNode> configuration) {
        this.contactSearchEngine = contactSearchEngine;
        this.ldapConnectionPool = ldapConnectionPool;
        this.ldapConfiguration = ldapConfiguration;
        this.objectClassFilter = Filter.createEqualityFilter("objectClass", ldapConfiguration.getUserObjectClass());
        this.userExtraFilter = Optional.ofNullable(ldapConfiguration.getFilter())
            .map(Throwing.function(Filter::create).sneakyThrow());
        this.firstnameAttribute = configuration.getString(FIRSTNAME_PROPERTY, "givenName");
        this.surnameAttribute = configuration.getString(SURNAME_PROPERTY, "sn");
        this.usernameAttribute = ldapConfiguration.getUsernameAttribute().orElse(ldapConfiguration.getUserIdAttribute());
        this.ignoredDomains = extractIgnoredDomains(configuration.getString(IGNORED_DOMAINS_PROPERTY, ""));
    }

    private List<Domain> extractIgnoredDomains(String domains) {
        if (domains.trim().isEmpty()) {
            return ImmutableList.of();
        }
        return Arrays.stream(domains.split(","))
            .map(Domain::of)
            .toList();
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxAdded
            || event instanceof MailboxDeletion;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxAdded) {
            return handleDomainContactAdded((MailboxAdded) event);
        }
        if (event instanceof MailboxDeletion) {
            return handleDomainContactDeletion((MailboxDeletion) event);
        }
        return Mono.empty();
    }

    private Publisher<Void> handleDomainContactAdded(MailboxAdded event) {
        if (event.getMailboxPath().isInbox()) {
            Username username = event.getUsername();
            Optional<Domain> maybeDomain = username.getDomainPart();
            return maybeDomain.map(domain -> addDomainContact(domain, username))
                .orElse(Mono.empty());
        }
        return Mono.empty();
    }

    private Mono<Void> addDomainContact(Domain domain, Username username) {
        if (ignoredDomains.contains(domain)) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> searchLdap(domain, username))
            .flatMap(contactFields -> Mono.from(contactSearchEngine.index(domain, contactFields)))
            .doOnError(error -> LOGGER.error("Error when indexing next contact for domain", error))
            .then();
    }

    private ContactFields searchLdap(Domain domain, Username username) throws LDAPSearchException, AddressException {
        Filter filter = createFilter(username.asString(), ldapConfiguration.getUserIdAttribute());
        SearchResult searchResult = ldapConnectionPool.search(userBase(domain), SearchScope.SUB, filter,
            firstnameAttribute, surnameAttribute, usernameAttribute);
        SearchResultEntry searchResultEntry = searchResult.getSearchEntries()
            .stream()
            .findFirst()
            .orElse(null);
        if (searchResultEntry == null) {
            LOGGER.warn("No LDAP entry found for user {}. Empty name values by default", username.asString());
            return new ContactFields(username.asMailAddress(), "", "");
        }

        return asContactFields(searchResultEntry);
    }

    private Filter createFilter(String retrievalName, String ldapUserRetrievalAttribute) {
        Filter specificUserFilter = Filter.createEqualityFilter(ldapUserRetrievalAttribute, retrievalName);
        return userExtraFilter
            .map(extraFilter -> Filter.createANDFilter(objectClassFilter, specificUserFilter, extraFilter))
            .orElseGet(() -> Filter.createANDFilter(objectClassFilter, specificUserFilter));
    }

    private String userBase(Domain domain) {
        return ldapConfiguration.getPerDomainBaseDN()
            .getOrDefault(domain, ldapConfiguration.getUserBase());
    }

    private ContactFields asContactFields(SearchResultEntry ldapEntry) throws AddressException {
        MailAddress mailAddress = Username.of(ldapEntry.getAttributeValue(usernameAttribute)).asMailAddress();
        Optional<String> firstname = Optional.ofNullable(ldapEntry.getAttributeValue(firstnameAttribute));
        Optional<String> surname = Optional.ofNullable(ldapEntry.getAttributeValue(surnameAttribute));
        return new ContactFields(mailAddress, firstname.orElse(""), surname.orElse(""));
    }

    private Publisher<Void> handleDomainContactDeletion(MailboxDeletion event) {
        if (event.getMailboxPath().isInbox()) {
            Username username = event.getUsername();
            Optional<Domain> maybeDomain = username.getDomainPart();
            return maybeDomain.map(domain -> removeDomainContact(domain, username))
                .orElse(Mono.empty());
        }
        return Mono.empty();
    }

    private Mono<Void> removeDomainContact(Domain domain, Username username) {
        if (ignoredDomains.contains(domain)) {
            return Mono.empty();
        }
        return Mono.fromCallable(username::asMailAddress)
            .flatMap(mailAddress -> Mono.from(contactSearchEngine.delete(domain, mailAddress)))
            .doOnError(error -> LOGGER.error("Error when indexing next contact for domain", error))
            .then();
    }
}
