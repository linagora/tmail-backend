package com.linagora.tmail.smtp;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.smtpserver.fastfail.ValidRcptHandler;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;

import com.google.common.annotations.VisibleForTesting;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

import reactor.core.publisher.Mono;

public class TMailWithMailingListValidRcptHandler extends ValidRcptHandler {
    private final TeamMailboxRepository teamMailboxRepository;
    private final LDAPConnectionPool ldapConnectionPool;
    private Filter objectClassFilter;
    private String baseDN;
    private String mailAttributeForGroups;

    @Inject
    public TMailWithMailingListValidRcptHandler(UsersRepository users,
                                                RecipientRewriteTable recipientRewriteTable,
                                                DomainList domains,
                                                TeamMailboxRepository teamMailboxRepository,
                                                LDAPConnectionPool ldapConnectionPool) {
        super(users, recipientRewriteTable, domains);
        this.teamMailboxRepository = teamMailboxRepository;
        this.ldapConnectionPool = ldapConnectionPool;
    }

    @VisibleForTesting
    public TMailWithMailingListValidRcptHandler(UsersRepository users,
                                                RecipientRewriteTable recipientRewriteTable,
                                                DomainList domains,
                                                TeamMailboxRepository teamMailboxRepository,
                                                LdapRepositoryConfiguration configuration) {
        super(users, recipientRewriteTable, domains);
        this.teamMailboxRepository = teamMailboxRepository;
        try {
            this.ldapConnectionPool = new LDAPConnectionFactory(configuration).getLdapConnectionPool();
        } catch (LDAPException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isValidRecipient(SMTPSession session, MailAddress recipient) throws UsersRepositoryException, RecipientRewriteTableException {
        return super.isValidRecipient(session, recipient)
            || isATeamMailbox(recipient)
            || isAGroup(recipient);
    }

    private boolean isATeamMailbox(MailAddress recipient) {
        return TeamMailbox.asTeamMailbox(recipient)
            .map(tm -> Mono.from(teamMailboxRepository.exists(tm)).block())
            .getOrElse(() -> false);
    }

    private boolean isAGroup(MailAddress recipient) {
        try {
            return resolveListDN(recipient).isPresent();
        } catch (LDAPSearchException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<SearchResultEntry> resolveListDN(MailAddress rcpt) throws LDAPSearchException {
        try {
            SearchResult searchResult = ldapConnectionPool.search(baseDN,
                SearchScope.SUB,
                createFilter(rcpt.asString(), mailAttributeForGroups));

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
        return Filter.createANDFilter(objectClassFilter, specificUserFilter);
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        super.init(config);
        baseDN = config.getString("baseDN");
        mailAttributeForGroups = config.getString("mailAttributeForGroups", "mail");
        String groupObjectClass = config.getString("groupObjectClass", "groupofnames");
        objectClassFilter = Filter.createEqualityFilter("objectClass", groupObjectClass);
    }
}
