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
 *******************************************************************/

package com.linagora.tmail.james.jmap.event;

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
import org.apache.james.jmap.api.identity.IdentityCreationRequest;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.api.model.Identity;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class IdentityProvisionListener implements EventListener.ReactiveGroupEventListener {
    public static class IdentityProvisionerListenerGroup extends Group {

    }

    private static final IdentityProvisionerListenerGroup GROUP = new IdentityProvisionerListenerGroup();
    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityProvisionListener.class);
    private static final int DEFAULT_IDENTITY_SORT_ORDER = 0;

    private final LDAPConnectionPool ldapConnectionPool;
    private final IdentityRepository identityRepository;
    private final LdapRepositoryConfiguration ldapConfiguration;
    private final Filter objectClassFilter;
    private final Optional<Filter> userExtraFilter;
    private final String firstnameAttribute;
    private final String surnameAttribute;
    private final String usernameAttribute;

    @Inject
    public IdentityProvisionListener(LDAPConnectionPool ldapConnectionPool,
                                     LdapRepositoryConfiguration ldapConfiguration,
                                     IdentityRepository identityRepository,
                                     HierarchicalConfiguration<ImmutableNode> listenerConfig) {
        this.ldapConnectionPool = ldapConnectionPool;
        this.ldapConfiguration = ldapConfiguration;
        this.identityRepository = identityRepository;
        this.objectClassFilter = Filter.createEqualityFilter("objectClass", ldapConfiguration.getUserObjectClass());
        this.userExtraFilter = Optional.ofNullable(ldapConfiguration.getFilter())
            .map(Throwing.function(Filter::create).sneakyThrow());
        this.firstnameAttribute = listenerConfig.getString("firstnameAttribute", "givenName");
        this.surnameAttribute = listenerConfig.getString("surnameAttribute", "sn");
        this.usernameAttribute = ldapConfiguration.getUsernameAttribute().orElse(ldapConfiguration.getUserIdAttribute());
    }

    @VisibleForTesting
    public IdentityProvisionListener(LdapRepositoryConfiguration ldapConfiguration,
                                     IdentityRepository identityRepository) throws LDAPException {
        this.ldapConnectionPool = new LDAPConnectionFactory(ldapConfiguration).getLdapConnectionPool();
        this.ldapConfiguration = ldapConfiguration;
        this.identityRepository = identityRepository;
        this.objectClassFilter = Filter.createEqualityFilter("objectClass", ldapConfiguration.getUserObjectClass());
        this.userExtraFilter = Optional.ofNullable(ldapConfiguration.getFilter())
            .map(Throwing.function(Filter::create).sneakyThrow());
        this.firstnameAttribute = "givenName";
        this.surnameAttribute = "sn";
        this.usernameAttribute = ldapConfiguration.getUsernameAttribute().orElse(ldapConfiguration.getUserIdAttribute());
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return isInboxCreated(event);
    }

    private boolean isInboxCreated(Event event) {
        if (event instanceof MailboxEvents.MailboxAdded mailboxAdded) {
            return mailboxAdded.getMailboxPath().getName().equalsIgnoreCase(MailboxConstants.INBOX)
                && mailboxAdded.getMailboxPath().getUser().equals(event.getUsername());
        }
        return false;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (!isHandling(event)) {
            return Mono.empty();
        }

        Username username = event.getUsername();
        return Mono.fromCallable(() -> searchLdap(event, username))
            .map(Throwing.function(this::asIdentityCreationRequest))
            .flatMap(identityCreationRequest -> hasNoDefaultIdentity(username)
                .flatMap(noDefaultIdentity -> Mono.from(identityRepository.save(username, identityCreationRequest))
                    .doOnSuccess(identity -> LOGGER.info("Created default identity for user {} with display name {}", username.asString(), identity.name()))))
            .then()
            .onErrorResume(e -> {
                LOGGER.error("Unexpected error during JMAP identity provisioning for user {}", event.getUsername(), e);
                return Mono.empty();
            })
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private SearchResultEntry searchLdap(Event event, Username username) throws LDAPSearchException {
        Filter filter = createFilter(username.asString(), evaluateLdapUserRetrievalAttribute(username));
        SearchResult searchResult = ldapConnectionPool.search(userBase(event.getUsername()), SearchScope.SUB, filter,
            firstnameAttribute, surnameAttribute, usernameAttribute);
        SearchResultEntry searchResultEntry = searchResult.getSearchEntries()
            .stream()
            .findFirst()
            .orElse(null);
        if (searchResultEntry == null) {
            LOGGER.warn("No LDAP entry found for user {}. Cannot provision identity.", event.getUsername());
        }

        return searchResultEntry;
    }

    private IdentityCreationRequest asIdentityCreationRequest(SearchResultEntry ldapEntry) throws AddressException {
        MailAddress mailAddress = Username.of(ldapEntry.getAttributeValue(usernameAttribute)).asMailAddress();
        Optional<String> firstname = Optional.ofNullable(ldapEntry.getAttributeValue(firstnameAttribute));
        Optional<String> surname = Optional.ofNullable(ldapEntry.getAttributeValue(surnameAttribute));
        String identityDisplayName = toDisplayName(firstname, surname, mailAddress.asString());
        return asIdentityRequest(mailAddress, identityDisplayName);
    }

    private IdentityCreationRequest asIdentityRequest(MailAddress mailAddress, String identityDisplayName) {
        return IdentityCreationRequest.fromJava(
            mailAddress,
            Optional.of(identityDisplayName),
            Optional.empty(),
            Optional.empty(),
            Optional.of(DEFAULT_IDENTITY_SORT_ORDER),
            Optional.empty(),
            Optional.empty());
    }

    private String userBase(Domain domain) {
        return ldapConfiguration.getPerDomainBaseDN()
            .getOrDefault(domain, ldapConfiguration.getUserBase());
    }

    private String userBase(Username username) {
        return username.getDomainPart()
            .map(this::userBase)
            .orElse(ldapConfiguration.getUserBase());
    }

    private String evaluateLdapUserRetrievalAttribute(Username retrievalName) {
        if (retrievalName.asString().contains("@")) {
            return ldapConfiguration.getUserIdAttribute();
        } else {
            return ldapConfiguration.getResolveLocalPartAttribute()
                .orElse(ldapConfiguration.getUserIdAttribute());
        }
    }

    private Filter createFilter(String retrievalName, String ldapUserRetrievalAttribute) {
        Filter specificUserFilter = Filter.createEqualityFilter(ldapUserRetrievalAttribute, retrievalName);
        return userExtraFilter
            .map(extraFilter -> Filter.createANDFilter(objectClassFilter, specificUserFilter, extraFilter))
            .orElseGet(() -> Filter.createANDFilter(objectClassFilter, specificUserFilter));
    }

    private String toDisplayName(Optional<String> firstname, Optional<String> surname, String fallbackEmailAddress) {
        if (firstname.isPresent() && surname.isPresent()) {
            return firstname.get() + " " + surname.get();
        }
        return firstname.orElseGet(() -> surname.orElse(fallbackEmailAddress));
    }

    private Mono<Boolean> hasNoDefaultIdentity(Username username) {
        return Flux.from(identityRepository.list(username))
            .filter(Identity::mayDelete)
            .hasElements()
            .filter(FunctionalUtils.identityPredicate().negate());
    }
}

