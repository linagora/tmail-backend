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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.util.AuditTrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.mailet.MailingListConfiguration;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.RDN;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

/**
 * <p>View of the mailing lists stored in LDAP, backing the {@code /mailingLists} webadmin routes.</p>
 *
 * <p>Both the default groupOfNames schema (members resolved from the {@code member} / {@code owner} DN attributes) and
 * the OBM schema (members resolved from the {@code mailBox} / {@code externalContactEmail} mail attributes) are
 * supported for reads, depending on the {@code obm.compatibility} flag of the {@link MailingListConfiguration}.</p>
 *
 * <p>Write operations (create/delete a list, add/remove members and owners) are supported for both schemas:</p>
 * <ul>
 *     <li>For the default groupOfNames schema, members and owners are stored as {@code member} / {@code owner} DN
 *     attributes; an address that does not resolve to a user is rejected.</li>
 *     <li>For the OBM schema, members are stored as mail values: an address resolving to a user goes to
 *     {@code mailBox}, an unresolved address goes to {@code externalContactEmail}. The {@code obmGroup} schema has no
 *     owner concept, so owner management is rejected with a {@link MailingListManagementException.WriteNotSupported}.
 *     OBM lists are created with a minimal set of attributes (a {@code mailAccess} derived from the requested
 *     {@code businessCategory}, a default {@code gidNumber} and the {@code obmDomain} derived from the list
 *     address).</li>
 * </ul>
 *
 * <p>They rely on single valued LDAP {@code MODIFY} to remain idempotent and require the configured bind user to hold
 * write ACLs on the lists subtree.</p>
 */
public class LdapMailingListRepository implements MailingListRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(LdapMailingListRepository.class);
    private static final String OBM_GROUP_OBJECT_CLASS = "obmGroup";
    private static final String POSIX_GROUP_OBJECT_CLASS = "posixGroup";
    private static final String GROUP_OF_NAMES_OBJECT_CLASS = "groupofnames";
    private static final String OBJECT_CLASS_ATTRIBUTE = "objectClass";
    private static final String CN_ATTRIBUTE = "cn";
    private static final String BUSINESS_CATEGORY_ATTRIBUTE = "businessCategory";
    private static final String MEMBER_ATTRIBUTE = "member";
    private static final String OWNER_ATTRIBUTE = "owner";
    private static final String MAIL_BOX_ATTRIBUTE = "mailBox";
    private static final String EXTERNAL_CONTACT_EMAIL_ATTRIBUTE = "externalContactEmail";
    private static final String GID_NUMBER_ATTRIBUTE = "gidNumber";
    private static final String OBM_DOMAIN_ATTRIBUTE = "obmDomain";
    private static final String MAIL_ACCESS_ATTRIBUTE = "mailAccess";
    private static final String DEFAULT_GID_NUMBER = "1000";
    private static final String MAIL_ACCESS_PERMIT = "PERMIT";
    private static final String MAIL_ACCESS_REJECT = "REJECT";
    private static final String OPEN_LIST_CATEGORY = "openlist";
    private static final String MEMBER_RESTRICTED_LIST_CATEGORY = "memberrestrictedlist";

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

    @Override
    public void create(MailingList mailingList) {
        if (obmCompatibility) {
            createObmList(mailingList);
        } else {
            createGroupOfNamesList(mailingList);
        }
    }

    private void createGroupOfNamesList(MailingList mailingList) {
        MailAddress address = mailingList.address();
        if (resolveListDN(address).isPresent()) {
            throw new MailingListManagementException.AlreadyExists(address);
        }
        if (mailingList.members().isEmpty()) {
            throw new MailingListManagementException.EmptyList(address);
        }

        Entry entry = new Entry(newListDN(address));
        entry.addAttribute(OBJECT_CLASS_ATTRIBUTE, GROUP_OF_NAMES_OBJECT_CLASS);
        entry.addAttribute(CN_ATTRIBUTE, address.getLocalPart());
        entry.addAttribute(mailAttributeForGroups, address.asString());
        mailingList.businessCategory()
            .ifPresent(category -> entry.addAttribute(BUSINESS_CATEGORY_ATTRIBUTE, category));
        entry.addAttribute(MEMBER_ATTRIBUTE, resolveUserDNs(mailingList.members()));
        if (!mailingList.owners().isEmpty()) {
            entry.addAttribute(OWNER_ATTRIBUTE, resolveUserDNs(mailingList.owners()));
        }

        addEntry(entry, address);
    }

    /**
     * Creates an OBM {@code obmGroup} list with a minimal set of attributes: a default {@code gidNumber}, a
     * {@code mailAccess} derived from the requested {@code businessCategory} and the {@code obmDomain} derived from the
     * list address. Members are stored as mail values rather than DNs: an address resolving to a user goes to
     * {@code mailBox}, an unresolved address to {@code externalContactEmail}. Owners are ignored: the {@code obmGroup}
     * schema has no owner concept.
     *
     * <p>The OBM mailet authorizes senders solely from {@code mailAccess} (see {@code OBMLDAPMailingList}), so the
     * {@code businessCategory} is mapped to it: {@code openList} (or no category) yields {@code PERMIT} (public), while
     * {@code memberRestrictedList} yields {@code REJECT} (only members may post). The remaining categories
     * ({@code internalList}, {@code ownerRestrictedList}, {@code domainRestrictedList}) have no OBM equivalent and are
     * rejected rather than silently created as public.</p>
     */
    private void createObmList(MailingList mailingList) {
        MailAddress address = mailingList.address();
        if (resolveListDN(address).isPresent()) {
            throw new MailingListManagementException.AlreadyExists(address);
        }
        String mailAccess = mailingList.businessCategory()
            .map(this::toObmMailAccess)
            .orElse(MAIL_ACCESS_PERMIT);

        Entry entry = new Entry(newListDN(address));
        entry.addAttribute(OBJECT_CLASS_ATTRIBUTE, OBM_GROUP_OBJECT_CLASS, POSIX_GROUP_OBJECT_CLASS);
        entry.addAttribute(CN_ATTRIBUTE, address.getLocalPart());
        entry.addAttribute(GID_NUMBER_ATTRIBUTE, DEFAULT_GID_NUMBER);
        entry.addAttribute(MAIL_ACCESS_ATTRIBUTE, mailAccess);
        entry.addAttribute(OBM_DOMAIN_ATTRIBUTE, address.getDomain().asString());
        entry.addAttribute(mailAttributeForGroups, address.asString());
        mailingList.businessCategory()
            .ifPresent(category -> entry.addAttribute(BUSINESS_CATEGORY_ATTRIBUTE, category));

        List<String> internalMembers = new ArrayList<>();
        List<String> externalMembers = new ArrayList<>();
        mailingList.members().forEach(member -> {
            if (isInternalUser(member)) {
                internalMembers.add(member.asString());
            } else {
                externalMembers.add(member.asString());
            }
        });
        if (!internalMembers.isEmpty()) {
            entry.addAttribute(MAIL_BOX_ATTRIBUTE, internalMembers.toArray(String[]::new));
        }
        if (!externalMembers.isEmpty()) {
            entry.addAttribute(EXTERNAL_CONTACT_EMAIL_ATTRIBUTE, externalMembers.toArray(String[]::new));
        }

        addEntry(entry, address);
    }

    private void addEntry(Entry entry, MailAddress address) {
        try {
            ldapConnectionPool.add(entry);
        } catch (LDAPException e) {
            if (e.getResultCode().equals(ResultCode.ENTRY_ALREADY_EXISTS)) {
                throw new MailingListManagementException.AlreadyExists(address);
            }
            throw new RuntimeException(e);
        }
        auditTrail("createMailingList", address, ImmutableMap.of());
    }

    @Override
    public void delete(MailAddress address) {
        String listDN = resolveListDN(address)
            .orElseThrow(() -> new MailingListManagementException.NotFound(address));
        try {
            ldapConnectionPool.delete(listDN);
        } catch (LDAPException e) {
            if (e.getResultCode().equals(ResultCode.NO_SUCH_OBJECT)) {
                throw new MailingListManagementException.NotFound(address);
            }
            throw new RuntimeException(e);
        }
        auditTrail("deleteMailingList", address, ImmutableMap.of());
    }

    @Override
    public void addMember(MailAddress listAddress, MailAddress member) {
        if (obmCompatibility) {
            addObmMember(listAddress, member);
        } else {
            modifyMembership(listAddress, member, MEMBER_ATTRIBUTE, ModificationType.ADD, "addMember");
        }
    }

    @Override
    public void removeMember(MailAddress listAddress, MailAddress member) {
        if (obmCompatibility) {
            removeObmMember(listAddress, member);
        } else {
            modifyMembership(listAddress, member, MEMBER_ATTRIBUTE, ModificationType.DELETE, "removeMember");
        }
    }

    @Override
    public void addOwner(MailAddress listAddress, MailAddress owner) {
        assertOwnerSupported();
        modifyMembership(listAddress, owner, OWNER_ATTRIBUTE, ModificationType.ADD, "addOwner");
    }

    @Override
    public void removeOwner(MailAddress listAddress, MailAddress owner) {
        assertOwnerSupported();
        modifyMembership(listAddress, owner, OWNER_ATTRIBUTE, ModificationType.DELETE, "removeOwner");
    }

    /**
     * Adds a member to an OBM list, storing it as a {@code mailBox} value when it resolves to a user and as an
     * {@code externalContactEmail} value otherwise. The single valued LDAP {@code MODIFY} stays idempotent.
     */
    private void addObmMember(MailAddress listAddress, MailAddress member) {
        String listDN = resolveListDN(listAddress)
            .orElseThrow(() -> new MailingListManagementException.NotFound(listAddress));
        String attribute = isInternalUser(member) ? MAIL_BOX_ATTRIBUTE : EXTERNAL_CONTACT_EMAIL_ATTRIBUTE;
        modifyMailValue(listDN, attribute, member.asString(), ModificationType.ADD);
        auditTrail("addMember", listAddress, ImmutableMap.of(MEMBER_ATTRIBUTE, member.asString()));
    }

    /**
     * Removes a member from an OBM list. As the address may have been stored either as an internal {@code mailBox} or
     * an external contact, it is stripped from both attributes; each removal is idempotent.
     */
    private void removeObmMember(MailAddress listAddress, MailAddress member) {
        String listDN = resolveListDN(listAddress)
            .orElseThrow(() -> new MailingListManagementException.NotFound(listAddress));
        modifyMailValue(listDN, MAIL_BOX_ATTRIBUTE, member.asString(), ModificationType.DELETE);
        modifyMailValue(listDN, EXTERNAL_CONTACT_EMAIL_ATTRIBUTE, member.asString(), ModificationType.DELETE);
        auditTrail("removeMember", listAddress, ImmutableMap.of(MEMBER_ATTRIBUTE, member.asString()));
    }

    private void modifyMailValue(String listDN, String attribute, String value, ModificationType type) {
        try {
            ldapConnectionPool.modify(listDN, new Modification(type, attribute, value));
        } catch (LDAPException e) {
            ResultCode resultCode = e.getResultCode();
            if (type == ModificationType.ADD && resultCode.equals(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS)) {
                return;
            }
            if (type == ModificationType.DELETE && resultCode.equals(ResultCode.NO_SUCH_ATTRIBUTE)) {
                return;
            }
            throw new RuntimeException(e);
        }
    }

    private void assertOwnerSupported() {
        if (obmCompatibility) {
            throw new MailingListManagementException.WriteNotSupported(
                "Owner management is not supported for OBM (obmGroup) mailing lists");
        }
    }

    /**
     * Adds or removes a single {@code member} / {@code owner} DN value on a list, relying on LDAP {@code MODIFY} of a
     * single attribute value to stay naturally atomic and idempotent: adding an already present value or removing an
     * absent one is a no-op success rather than an error.
     */
    private void modifyMembership(MailAddress listAddress, MailAddress user, String attribute,
                                  ModificationType type, String action) {
        String listDN = resolveListDN(listAddress)
            .orElseThrow(() -> new MailingListManagementException.NotFound(listAddress));
        String userDN = resolveUserDN(user);
        try {
            ldapConnectionPool.modify(listDN, new Modification(type, attribute, userDN));
        } catch (LDAPException e) {
            ResultCode resultCode = e.getResultCode();
            if (type == ModificationType.ADD && resultCode.equals(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS)) {
                return;
            }
            if (type == ModificationType.DELETE && resultCode.equals(ResultCode.NO_SUCH_ATTRIBUTE)) {
                return;
            }
            if (resultCode.equals(ResultCode.OBJECT_CLASS_VIOLATION)) {
                throw new MailingListManagementException.EmptyList(listAddress);
            }
            throw new RuntimeException(e);
        }
        auditTrail(action, listAddress, ImmutableMap.of(attribute, user.asString()));
    }

    /**
     * Maps a webadmin {@code businessCategory} to an OBM {@code mailAccess} value. Only the two categories that OBM can
     * represent are supported; the others are rejected so that a restricted list is never silently created as public.
     */
    private String toObmMailAccess(String businessCategory) {
        return switch (businessCategory.toLowerCase().trim()) {
            case OPEN_LIST_CATEGORY -> MAIL_ACCESS_PERMIT;
            case MEMBER_RESTRICTED_LIST_CATEGORY -> MAIL_ACCESS_REJECT;
            default -> throw new MailingListManagementException.WriteNotSupported(
                "businessCategory '" + businessCategory + "' is not supported for OBM (obmGroup) mailing lists; "
                    + "only openList and memberRestrictedList are supported");
        };
    }

    /**
     * Builds the DN of a new list. The RDN combines {@code cn} (the local part, keeping the group naming) with the mail
     * attribute (the full address) so that two lists sharing a local part in different domains map to distinct DNs
     * under the same {@code baseDN}. Lookups still rely on the mail attribute, so this only affects entry creation.
     */
    private String newListDN(MailAddress address) {
        RDN rdn = new RDN(
            new String[]{CN_ATTRIBUTE, mailAttributeForGroups},
            new String[]{address.getLocalPart(), address.asString()});
        return rdn + "," + baseDN;
    }

    private Optional<String> resolveListDN(MailAddress address) {
        Filter filter = Filter.createANDFilter(objectClassFilter,
            Filter.createEqualityFilter(mailAttributeForGroups, address.asString()));
        return search(filter, mailAttributeForGroups).stream()
            .findFirst()
            .map(SearchResultEntry::getDN);
    }

    private String[] resolveUserDNs(List<MailAddress> addresses) {
        return addresses.stream()
            .map(this::resolveUserDN)
            .toArray(String[]::new);
    }

    /**
     * Tells whether an address resolves to a user entry in the configured {@code userBase}. Used by the OBM writes to
     * decide between storing an address as an internal {@code mailBox} or an external {@code externalContactEmail}.
     */
    private boolean isInternalUser(MailAddress user) {
        Filter filter = Filter.createEqualityFilter(ldapConfiguration.getUserIdAttribute(), user.asString());
        try {
            return !ldapConnectionPool.search(ldapConfiguration.getUserBase(), SearchScope.SUB, filter)
                .getSearchEntries().isEmpty();
        } catch (LDAPException e) {
            if (e.getResultCode().equals(ResultCode.NO_SUCH_OBJECT)) {
                return false;
            }
            throw new RuntimeException(e);
        }
    }

    private String resolveUserDN(MailAddress user) {
        Filter filter = Filter.createEqualityFilter(ldapConfiguration.getUserIdAttribute(), user.asString());
        List<SearchResultEntry> entries;
        try {
            entries = ldapConnectionPool.search(ldapConfiguration.getUserBase(), SearchScope.SUB, filter)
                .getSearchEntries();
        } catch (LDAPException e) {
            if (e.getResultCode().equals(ResultCode.NO_SUCH_OBJECT)) {
                throw new MailingListManagementException.UnknownMember(user);
            }
            throw new RuntimeException(e);
        }
        if (entries.isEmpty()) {
            throw new MailingListManagementException.UnknownMember(user);
        }
        if (entries.size() > 1) {
            LOGGER.warn("The address {} resolves to several LDAP entries, picking the first one", user.asString());
        }
        return entries.get(0).getDN();
    }

    private void auditTrail(String action, MailAddress listAddress, Map<String, String> extraParameters) {
        AuditTrail.entry()
            .protocol("webadmin")
            .action(action)
            .parameters(() -> ImmutableMap.<String, String>builder()
                .put("listAddress", listAddress.asString())
                .putAll(extraParameters)
                .build())
            .log("Mailing list management operation.");
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
