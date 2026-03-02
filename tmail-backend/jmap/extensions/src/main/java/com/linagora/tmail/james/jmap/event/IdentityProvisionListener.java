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

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
import org.apache.james.jmap.api.model.IdentityId;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.GuiceLoader;
import org.apache.james.utils.NamingScheme;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.team.TeamMailbox;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;

public class IdentityProvisionListener implements EventListener.ReactiveGroupEventListener {
    public static class IdentityProvisionerListenerGroup extends Group {

    }

    private static final IdentityProvisionerListenerGroup GROUP = new IdentityProvisionerListenerGroup();
    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityProvisionListener.class);
    private static final int DEFAULT_IDENTITY_SORT_ORDER = 0;
    private static final String APPLY_WHEN_PATH = "defaultText.applyWhen";

    private final LDAPConnectionPool ldapConnectionPool;
    private final IdentityRepository identityRepository;
    private final LdapRepositoryConfiguration ldapConfiguration;
    private final Filter objectClassFilter;
    private final Optional<Filter> userExtraFilter;
    private final String firstnameAttribute;
    private final String surnameAttribute;
    private final String usernameAttribute;
    private final SignatureTextFactory signatureTextFactory;
    private final Set<String> attributes;

    @Inject
    public IdentityProvisionListener(LDAPConnectionPool ldapConnectionPool,
                                     LdapRepositoryConfiguration ldapConfiguration,
                                     IdentityRepository identityRepository,
                                     JmapSettingsRepository jmapSettingsRepository,
                                     GuiceLoader guiceLoader,
                                     HierarchicalConfiguration<ImmutableNode> listenerConfig) {
        this.ldapConnectionPool = ldapConnectionPool;
        this.ldapConfiguration = ldapConfiguration;
        this.identityRepository = identityRepository;
        this.signatureTextFactory = new DefaultSignatureTextFactory(jmapSettingsRepository, listenerConfig, resolveApplyWhenFilter(listenerConfig, guiceLoader));
        this.objectClassFilter = Filter.createEqualityFilter("objectClass", ldapConfiguration.getUserObjectClass());
        this.userExtraFilter = Optional.ofNullable(ldapConfiguration.getFilter())
            .map(Throwing.function(Filter::create).sneakyThrow());
        this.firstnameAttribute = listenerConfig.getString("firstnameAttribute", "givenName");
        this.surnameAttribute = listenerConfig.getString("surnameAttribute", "sn");
        this.usernameAttribute = ldapConfiguration.getUsernameAttribute().orElse(ldapConfiguration.getUserIdAttribute());
        this.attributes = Optional.ofNullable(listenerConfig.getStringArray("extraAttributes")).map(values -> ImmutableSet.<String>builder().addAll(ImmutableSet.copyOf(values)))
            .orElse(ImmutableSet.builder())
            .add(firstnameAttribute)
            .add(surnameAttribute)
            .add(usernameAttribute)
            .build();
    }

    private ApplyWhenFilter resolveApplyWhenFilter(HierarchicalConfiguration<ImmutableNode> listenerConfig, GuiceLoader guiceLoader) {
        return Optional.ofNullable(listenerConfig.getString(APPLY_WHEN_PATH))
            .filter(applyWhenClassName -> !applyWhenClassName.isBlank())
            .map(applyWhenClassName -> loadApplyWhenFilter(guiceLoader, applyWhenClassName))
            .orElse(new ApplyWhenFilter.Always());
    }

    private ApplyWhenFilter loadApplyWhenFilter(GuiceLoader guiceLoader, String applyWhenClassName) {
        try {
            return guiceLoader.<ApplyWhenFilter>withNamingSheme(NamingScheme.IDENTITY)
                .instantiate(new ClassName(applyWhenClassName));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load applyWhen filter `%s`".formatted(applyWhenClassName), e);
        }
    }

    @VisibleForTesting
    public IdentityProvisionListener(LdapRepositoryConfiguration ldapConfiguration,
                                     IdentityRepository identityRepository,
                                     SignatureTextFactory signatureTextFactory,
                                     Collection<String> extraAttributes) throws LDAPException {
        this.ldapConnectionPool = new LDAPConnectionFactory(ldapConfiguration).getLdapConnectionPool();
        this.ldapConfiguration = ldapConfiguration;
        this.identityRepository = identityRepository;
        this.signatureTextFactory = signatureTextFactory;
        this.objectClassFilter = Filter.createEqualityFilter("objectClass", ldapConfiguration.getUserObjectClass());
        this.userExtraFilter = Optional.ofNullable(ldapConfiguration.getFilter())
            .map(Throwing.function(Filter::create).sneakyThrow());
        this.firstnameAttribute = "givenName";
        this.surnameAttribute = "sn";
        this.usernameAttribute = ldapConfiguration.getUsernameAttribute().orElse(ldapConfiguration.getUserIdAttribute());
        this.attributes = ImmutableSet.<String>builder()
            .add(firstnameAttribute)
            .add(surnameAttribute)
            .add(usernameAttribute)
            .addAll(extraAttributes)
            .build();
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return isInboxCreated(event) || isBaseTeamMailboxACLUpdated(event);
    }

    private boolean isInboxCreated(Event event) {
        if (event instanceof MailboxEvents.MailboxAdded mailboxAdded) {
            return mailboxAdded.getMailboxPath().getName().equalsIgnoreCase(MailboxConstants.INBOX)
                && mailboxAdded.getMailboxPath().getUser().equals(event.getUsername());
        }
        return false;
    }

    private boolean isBaseTeamMailboxACLUpdated(Event event) {
        if (event instanceof MailboxEvents.MailboxACLUpdated aclUpdated) {
            return OptionConverters.toJava(TeamMailbox.from(aclUpdated.getMailboxPath()))
                .map(teamMailbox -> teamMailbox.mailboxPath().equals(aclUpdated.getMailboxPath()))
                .orElse(false);
        }
        return false;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxEvents.MailboxACLUpdated aclUpdated && isBaseTeamMailboxACLUpdated(aclUpdated)) {
            return handleTeamMailboxACLUpdated(aclUpdated);
        }
        if (isInboxCreated(event)) {
            return handleInboxCreated(event);
        }
        return Mono.empty();
    }

    private Publisher<Void> handleInboxCreated(Event event) {
        Username username = event.getUsername();
        return Mono.fromCallable(() -> searchLdap(username))
            .flatMap(ldapEntry -> asIdentityCreationRequest(username, ldapEntry))
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

    private Publisher<Void> handleTeamMailboxACLUpdated(MailboxEvents.MailboxACLUpdated event) {
        TeamMailbox teamMailbox = OptionConverters.toJava(TeamMailbox.from(event.getMailboxPath())).orElseThrow();
        Set<String> systemUsernames = Set.of(
            teamMailbox.owner().asString(),
            teamMailbox.admin().asString(),
            teamMailbox.self().asString());
        ACLDiff aclDiff = event.getAclDiff();

        Flux<Void> provisions = usersWhoGainedPostRight(aclDiff, systemUsernames)
            .flatMap(user -> provisionTeamMailboxIdentity(user, teamMailbox));
        Flux<Void> removals = usersWhoLostPostRight(aclDiff, systemUsernames)
            .flatMap(user -> removeTeamMailboxIdentity(user, teamMailbox));

        return Flux.merge(provisions, removals)
            .then()
            .onErrorResume(e -> {
                LOGGER.error("Unexpected error during team mailbox identity provisioning for {}", teamMailbox.asString(), e);
                return Mono.empty();
            });
    }

    private Flux<Username> usersWhoGainedPostRight(ACLDiff aclDiff, Set<String> systemUsernames) {
        MailboxACL oldACL = aclDiff.getOldACL();
        return Flux.fromIterable(aclDiff.getNewACL().getEntries().entrySet())
            .filter(e -> !e.getKey().isNegative())
            .filter(e -> MailboxACL.NameType.user.equals(e.getKey().getNameType()))
            .filter(e -> !systemUsernames.contains(e.getKey().getName()))
            .filter(e -> e.getValue().contains(MailboxACL.Right.Post))
            .filter(e -> {
                MailboxACL.Rfc4314Rights oldRights = oldACL.getEntries().get(e.getKey());
                return oldRights == null || !oldRights.contains(MailboxACL.Right.Post);
            })
            .map(e -> Username.of(e.getKey().getName()));
    }

    private Flux<Username> usersWhoLostPostRight(ACLDiff aclDiff, Set<String> systemUsernames) {
        MailboxACL newACL = aclDiff.getNewACL();
        return Flux.fromIterable(aclDiff.getOldACL().getEntries().entrySet())
            .filter(e -> !e.getKey().isNegative())
            .filter(e -> MailboxACL.NameType.user.equals(e.getKey().getNameType()))
            .filter(e -> !systemUsernames.contains(e.getKey().getName()))
            .filter(e -> e.getValue().contains(MailboxACL.Right.Post))
            .filter(e -> {
                MailboxACL.Rfc4314Rights newRights = newACL.getEntries().get(e.getKey());
                return newRights == null || !newRights.contains(MailboxACL.Right.Post);
            })
            .map(e -> Username.of(e.getKey().getName()));
    }

    private Mono<Void> provisionTeamMailboxIdentity(Username user, TeamMailbox teamMailbox) {
        MailAddress teamMailboxAddress = teamMailbox.asMailAddress();
        String identityName = teamMailbox.mailboxName().asString();
        return Flux.from(identityRepository.list(user))
            .filter(identity -> identity.email().equals(teamMailboxAddress))
            .filter(Identity::mayDelete)
            .hasElements()
            .filter(FunctionalUtils.identityPredicate().negate())
            .flatMap(noIdentity -> Mono.from(identityRepository.save(user, asIdentityRequest(teamMailboxAddress, identityName)))
                .doOnSuccess(identity -> LOGGER.info("Created team mailbox identity for user {} with email {}", user.asString(), teamMailboxAddress.asString())))
            .then();
    }

    private Mono<Void> removeTeamMailboxIdentity(Username user, TeamMailbox teamMailbox) {
        MailAddress teamMailboxAddress = teamMailbox.asMailAddress();
        return Flux.from(identityRepository.list(user))
            .filter(identity -> identity.email().equals(teamMailboxAddress))
            .map(Identity::id)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new))
            .flatMap(javaIds -> {
                if (javaIds.isEmpty()) {
                    return Mono.empty();
                }
                scala.collection.immutable.Set<IdentityId> scalaIds = CollectionConverters.asScala(javaIds).toSet();
                return Mono.from(identityRepository.delete(user, scalaIds));
            })
            .then();
    }

    private SearchResultEntry searchLdap(Username username) throws LDAPSearchException {
        Filter filter = createFilter(username.asString(), evaluateLdapUserRetrievalAttribute(username));
        SearchResult searchResult = ldapConnectionPool.search(userBase(username), SearchScope.SUB, filter,
            attributes.toArray(new String[0]));
        SearchResultEntry searchResultEntry = searchResult.getSearchEntries()
            .stream()
            .findFirst()
            .orElse(null);
        if (searchResultEntry == null) {
            LOGGER.warn("No LDAP entry found for user {}. Cannot provision identity.", username.asString());
        }

        return searchResultEntry;
    }

    private Mono<IdentityCreationRequest> asIdentityCreationRequest(Username username, SearchResultEntry ldapEntry) {
        try {
            MailAddress mailAddress = Username.of(ldapEntry.getAttributeValue(usernameAttribute)).asMailAddress();
            Optional<String> firstname = Optional.ofNullable(ldapEntry.getAttributeValue(firstnameAttribute));
            Optional<String> surname = Optional.ofNullable(ldapEntry.getAttributeValue(surnameAttribute));
            String identityDisplayName = toDisplayName(firstname, surname, mailAddress.asString());
            return signatureTextFactory.forUser(username)
                .map(signatureOptional -> signatureOptional.map(signature -> signature.interpolate(ldapEntry.getAttributes().stream()
                    .filter(attribute -> attribute.getValue() != null)
                    .collect(ImmutableMap.toImmutableMap(
                        Attribute::getName,
                        Attribute::getValue)))))
                .map(signatureOptional -> signatureOptional
                    .map(signature -> asIdentityRequest(mailAddress, identityDisplayName, signature))
                    .orElseGet(() -> asIdentityRequest(mailAddress, identityDisplayName)));
        } catch (AddressException e) {
            return Mono.error(e);
        }
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

    private IdentityCreationRequest asIdentityRequest(MailAddress mailAddress, String identityDisplayName, SignatureText signatureText) {
        return IdentityCreationRequest.fromJava(
            mailAddress,
            Optional.of(identityDisplayName),
            Optional.empty(),
            Optional.empty(),
            Optional.of(DEFAULT_IDENTITY_SORT_ORDER),
            Optional.ofNullable(signatureText.textSignature()),
            Optional.ofNullable(signatureText.htmlSignature()));
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

