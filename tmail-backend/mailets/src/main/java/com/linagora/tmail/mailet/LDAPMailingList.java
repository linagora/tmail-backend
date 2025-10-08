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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.util.AuditTrail;
import org.apache.james.util.DurationParser;
import org.apache.mailet.LoopPrevention;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.RDNNameValuePair;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

/**
 * <p>Mailing list resolution for Twake mail based on LDAP groups.</p>
 *
 * <p>This mailet will lookup mailAdress in order to identify groups and substitute their mail address with the ones of users.</p>
 *
 * <ul>Configuration:
 *  <li>mailingListPredicate: an heuristic to determine if a given mail address is likely to be a list
 *    Specify `lists-prefix` for only mailAddress `abc@lists.domain.tld` to be considered lists and expended
 *    Specify `any-local` to try to expend all local addresses which had a cost!</li>
 *  <li>baseDN: the base DN to use within group search. EG: ou=lists,dc=linagora,dc=com</li>
 *  <li>rejectedSenderProcessor: processor to handle rejected sender. Generally this is comprised of a bounce mailet explaining the
 *  rejection.</li>
 *  <li>userMailCacheSize: Number of users DN to keep in the cache. This saves DN lookups on member to retrieve their mail address.</li>
 *  <li>userMailCacheDuration: Time during which one should keep entries into the user DN => mailAddress cache.</li>
 *  <li>mailAttributeForGroups: Attribute holding the mail address of a group. For easy testing this can be set to description
 *  but for production use a special LDAP schema needs to be crafted for using the mail attribute.</li>
 *  <li>extraFilter: a LDAP filter to use when looking up the groups. None if not provided. Needs to match LDAP filter syntax.</li>
 *  </ul>
 *
 *  <ul>Performance considerations:
 *    <li>Sender validation goes faster if the email local part is also the user uid, as this heuristic saves precious LDAP lookups</li>
 *    <li>All DN lookups for retrieving mail address are cached for efficiency. This implies that email address changes for users
 *    is a rare event, and we accept it to be non-synchronized for the duration of the cache retention when this happens</li>
 *    <li>LDAP groups composition and validation rules are never cached so far: addition of new users into a group will
 *    thus be an instant operation.</li>
 *  </ul>
 *
 *  <p>Sample structure for LDAP groups:</p>
 *
 *  <pre><code># List open to potential external customers, contact point of the company
 * # sales@lists.linagora.com
 * cn=sales,ou=lists,dc=linagora,dc=com
 * businessCategory: openList
 * member: uid=hansolo,dc=linagora,dc=com
 * member: uid=leila,dc=linagora,dc=com
 *
 * # List open to employees to ask questions about Java to java experts
 * # internal-java-support@lists.linagora.com
 * cn=internal-java-support,ou=lists,dc=linagora,dc=com
 * businessCategory: internalList
 * member: uid=btellier,dc=linagora,dc=com
 * member: uid=rcordier,dc=linagora,dc=com
 *
 * # Private list reserverved solely to Cosoft members
 * # cosoft@lists.linagora.com
 * cn=cosoft,ou=lists,dc=linagora,dc=com
 * businessCategory: memberRestrictedList
 * member: uid=btellier,dc=linagora,dc=com
 * member: uid=xguimard,dc=linagora,dc=com
 *
 * # This list allows mailing all the staff and thus is restricted
 * # staff@lists.linagora.com
 * cn=staff,ou=lists,dc=linagora,dc=com
 * businessCategory: ownerRestrictedList
 * owner: uid=vsteffen,dc=linagora,dc=com
 * owner: uid=azapolsky,dc=linagora,dc=com
 * owner: uid=mmaudet,dc=linagora,dc=com
 * member: uid=btellier,dc=linagora,dc=com
 * member: uid=mmaudet,dc=linagora,dc=com
 * member: uid=azapolsky,dc=linagora,dc=com
 * member: uid=vsteffen,dc=linagora,dc=com
 * member: uid=xguimard,dc=linagora,dc=com
 * member: ...
 *
 * # This list is only for vn office members with @vn.linagora.com addresses
 * # vnoffice@lists.vn.linagora.com
 * cn=vnoffice,ou=lists,dc=vn,dc=linagora,dc=com
 * businessCategory: domainRestrictedList
 * member: uid=vttran,dc=vn,dc=linagora,dc=com
 * member: uid=hphan,dc=vn,dc=linagora,dc=com
 * member: uid=hqtrong,dc=vn,dc=linagora,dc=com
 * member: ...</code></pre>
 */
public class LDAPMailingList extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(LDAPMailingList.class);

    interface MailingListPredicate extends Predicate<MailAddress> {
        MailingListPredicate LISTS_PREFIX = mailAddress -> mailAddress.getDomain().asString().startsWith("lists.");
        MailingListPredicate ALL = mailAddress -> true;
        Function<MailetContext, MailingListPredicate> ANY_LOCAL = mailetContext -> mailAddress -> mailetContext.isLocalServer(mailAddress.getDomain());
    }

    interface MailTransformation  {
        Mail transform(Mail mail) throws MessagingException;

        Function<MailAddress, MailTransformation> removeRecipient = rcpt -> mail -> {
            mail.setRecipients(mail.getRecipients()
                .stream()
                .filter(r -> !r.equals(rcpt))
                .collect(ImmutableList.toImmutableList()));
            return mail;
        };
        Function<Collection<MailAddress>, MailTransformation> addRecipients = rcpts -> mail -> {
            mail.setRecipients(ImmutableList.<MailAddress>builder()
                .addAll(rcpts)
                .addAll(mail.getRecipients())
                .build());
            return mail;
        };
        MailTransformation NOOP = mail -> mail;
        Function<MailAddress, MailTransformation> recordListInLoopDetection = listAddress -> mail -> {
            LoopPrevention.RecordedRecipients recordedRecipients = LoopPrevention.RecordedRecipients.fromMail(mail);
            recordedRecipients.merge(listAddress).recordOn(mail);
            return mail;
        };

        default MailTransformation doCompose(MailTransformation other) {
            return mail -> transform(other.transform(mail));
        }

        default MailTransformation doComposeIf(MailTransformation other, Predicate<Mail> condition) {
            return mail -> {
                if (condition.apply(mail)) {
                    return transform(other.transform(mail));
                }
                return transform(mail);
            };
        }
    }

    interface SenderValidationPolicy {
        boolean validate(MaybeSender sender, SearchResultEntry list);

        SenderValidationPolicy OPEN = (a, b) -> true; // Accept all mails
        Function<MailetContext, SenderValidationPolicy> INTERNAL = mailetContext -> (sender, list) -> sender.asOptional()
            .map(address -> mailetContext.isLocalServer(address.getDomain()))
            .orElse(false); // Accept local mails
    }

    private final LDAPConnectionPool ldapConnectionPool;
    private final LdapRepositoryConfiguration configuration;
    private Filter objectClassFilter;
    private String[] listAttributes;
    private LoadingCache<String, List<MailAddress>> userMailCache;
    private String baseDN;
    private String rejectedSenderProcessor;
    private MailingListPredicate mailingListPredicate;
    private String mailAttributeForGroups;
    private Optional<Filter> extraFilter = Optional.empty();

    @Inject
    public LDAPMailingList(LDAPConnectionPool ldapConnectionPool, LdapRepositoryConfiguration configuration) {
        this.configuration = configuration;
        this.ldapConnectionPool = ldapConnectionPool;
    }

    @VisibleForTesting
    public LDAPMailingList(LdapRepositoryConfiguration configuration) throws LDAPException {
        this(new LDAPConnectionFactory(configuration).getLdapConnectionPool(), configuration);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        mail.getRecipients()
            .stream()
            .filter(mailingListPredicate)
            .flatMap(Throwing.function(list -> resolveListDN(list).stream()))
            .map(entry -> listToMailTransformation(mail.getMaybeSender(), entry))
            .reduce(MailTransformation.NOOP, MailTransformation::doCompose)
            .transform(mail);
    }

    @Override
    public void init() throws MessagingException {
        baseDN = getInitParameter("baseDN");
        extraFilter = Optional.ofNullable(getInitParameter("extraFilter"))
            .map(Throwing.function(Filter::create));
        mailAttributeForGroups = getInitParameter("mailAttributeForGroups", "mail");
        String groupObjectClass = getInitParameter("groupObjectClass", "groupofnames");
        objectClassFilter = Filter.createEqualityFilter("objectClass", groupObjectClass);
        rejectedSenderProcessor = getInitParameter("rejectedSenderProcessor");
        mailingListPredicate = mailingListPredicate();

        int userMailCacheSize = Optional.ofNullable(getInitParameter("userMailCacheSize"))
            .map(Integer::parseInt)
            .orElse(10000);

        Duration userMailCacheDuration = Optional.ofNullable(getInitParameter("userMailCacheDuration"))
            .map(DurationParser::parse)
            .orElse(Duration.ofHours(1));

        CacheLoader<String, List<MailAddress>> cacheLoader = new CacheLoader<>() {
            @Override
            public List<MailAddress> load(String dn) throws Exception {
                SearchResultEntry entry = ldapConnectionPool.getEntry(dn,
                    ImmutableSet.builder()
                        .add(configuration.getUserIdAttribute())
                        .add("member")
                        .add("objectClass")
                        .build().toArray(String[]::new));
                if (entry == null) {
                    return ImmutableList.of();
                }
                if (entry.hasAttribute("member")) {
                    return Arrays.stream(entry.getAttribute("member").getValues())
                        .flatMap(nestedDn -> resolveUserMail(nestedDn).stream())
                        .collect(ImmutableList.toImmutableList());
                }
                if (entry.getAttribute(configuration.getUserIdAttribute()) == null) {
                    return ImmutableList.of();
                }
                return ImmutableList.of(new MailAddress(entry.getAttribute(configuration.getUserIdAttribute()).getValue()));
            }
        };
        this.userMailCache = CacheBuilder.newBuilder()
            .maximumSize(userMailCacheSize)
            .expireAfterAccess(userMailCacheDuration)
            .build(cacheLoader);

        this.listAttributes = ImmutableSet.builder()
            .add(mailAttributeForGroups)
            .add("member")
            .add("owner")
            .add("businessCategory")
            .build().toArray(String[]::new);
    }

    @Override
    public Collection<ProcessingState> requiredProcessingState() {
        return ImmutableList.of(new ProcessingState(rejectedSenderProcessor));
    }

    private MailingListPredicate mailingListPredicate() {
        String value = getInitParameter("mailingListPredicate", "lists-prefix");
        return switch (value) {
            case "lists-prefix" -> MailingListPredicate.LISTS_PREFIX;
            case "any-local" -> MailingListPredicate.ANY_LOCAL.apply(getMailetContext());
            case "all" -> MailingListPredicate.ALL;
            default -> throw new RuntimeException("Unsupported MailingListPredicate " + value);
        };
    }

    private Optional<SearchResultEntry> resolveListDN(MailAddress rcpt) throws LDAPSearchException {
        try {
            SearchResult searchResult = ldapConnectionPool.search(baseDN,
                SearchScope.SUB,
                createFilter(rcpt.asString(), mailAttributeForGroups),
                listAttributes);

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
        return extraFilter
            .map(filter -> Filter.createANDFilter(objectClassFilter, specificUserFilter, filter))
            .orElseGet(() -> Filter.createANDFilter(objectClassFilter, specificUserFilter));
    }

    private MailTransformation listToMailTransformation(MaybeSender maybeSender, SearchResultEntry list) {
        try {
            MailAddress listAddress = new MailAddress(list.getAttributeValue(mailAttributeForGroups));
            boolean authorized = chooseSenderValidationPolicy(list)
                .validate(maybeSender, list);

            if (!authorized) {
                return toRejectedProcessor(listAddress)
                    .doCompose(MailTransformation.removeRecipient.apply(listAddress))
                    .doCompose(mail -> {
                        AuditTrail.entry()
                            .protocol("mailetcontainer")
                            .action("list")
                            .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                                "mimeMessageId", Optional.ofNullable(mail.getMessage())
                                    .map(Throwing.function(MimeMessage::getMessageID))
                                    .orElse(""),
                                "sender", maybeSender.asString(),
                                "listAddress", listAddress.asString())))
                            .log("Rejected a mail to a mailing list.");
                        return mail;
                    });
            }

            List<MailAddress> memberAddresses = list.getAttributes().stream()
                .filter(attribute -> attribute.getName().equals("member"))
                .flatMap(attribute -> Stream.of(attribute.getValues()))
                .map(Throwing.function(this::resolveUserMail))
                .flatMap(Collection::stream)
                .collect(ImmutableList.toImmutableList());

            return MailTransformation.removeRecipient.apply(listAddress)
                .doComposeIf(
                    mail -> {

                        if (!memberAddresses.isEmpty()) {
                            Mail duplicate = mail.duplicate();
                            try {
                                MailTransformation.recordListInLoopDetection.apply(listAddress).transform(duplicate);
                                duplicate.setRecipients(memberAddresses);
                                addListHeaders(duplicate.getMessage(), listAddress);
                                getMailetContext().sendMail(duplicate);
                                AuditTrail.entry()
                                    .protocol("mailetcontainer")
                                    .action("list")
                                    .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                                        "mimeMessageId", Optional.ofNullable(mail.getMessage())
                                            .map(Throwing.function(MimeMessage::getMessageID))
                                            .orElse(""),
                                        "sender", maybeSender.asString(),
                                        "listAddress", listAddress.asString())))
                                    .log("Sent a mail to a mailing list.");
                            } finally {
                                LifecycleUtil.dispose(duplicate);
                            }
                        }

                        return mail;
                    },
                    mail -> !LoopPrevention.RecordedRecipients.fromMail(mail).getRecipients().contains(listAddress));
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }

    private void addListHeaders(MimeMessage message, MailAddress listName) throws MessagingException {
        message.addHeader("List-Id","<" + listName.asString() + ">");
        message.addHeader("List-Post","<mailto:" + listName.asString() + ">");
    }

    private MailTransformation toRejectedProcessor(MailAddress listAddress) {
        return mail -> {
            try {
                Mail newMail = mail.duplicate();
                newMail.setRecipients(ImmutableList.of(listAddress));
                try {
                    getMailetContext().sendMail(newMail, rejectedSenderProcessor);
                } finally {
                    LifecycleUtil.dispose(newMail);
                }
                return mail;
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private List<MailAddress> resolveUserMail(String dn) {
        try {
            return userMailCache.get(dn);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            if (e.getMessage().startsWith("Recursive load")) {
                LOGGER.error("Recursive LDAP group definition creates a loop for dn {}. Please review LDAP data", dn);
                return ImmutableList.of();
            }
            throw e;
        }
    }

   SenderValidationPolicy memberSenderValidationPolicy() {
       return (sender, list) -> {
           if (sender.isNullSender()) {
               return false;
           }
           Supplier<Boolean> isMemberUidHeuristic = () -> list.getAttributes().stream()
               .filter(attribute -> attribute.getName().equals("member"))
               .flatMap(attribute -> Stream.of(attribute.getValues()))
               .filter(value -> value.startsWith("uid=" + sender.asString()))
               .map(Throwing.function(this::resolveUserMail))
               .flatMap(Collection::stream)
               .anyMatch(sender.asOptional().get()::equals);
           Supplier<Boolean> isMember = () -> list.getAttributes().stream()
               .filter(attribute -> attribute.getName().equals("member"))
               .flatMap(attribute -> Stream.of(attribute.getValues()))
               .map(Throwing.function(this::resolveUserMail))
               .flatMap(Collection::stream)
               .anyMatch(sender.asOptional().get()::equals);

           return isMemberUidHeuristic.get() || isMember.get();
       };
   }

   SenderValidationPolicy ownerSenderValidationPolicy() {
       return (sender, list) -> {
           if (sender.isNullSender()) {
               return false;
           }
           Supplier<Boolean> isOwnerUidHeuristic = () -> list.getAttributes().stream()
               .filter(attribute -> attribute.getName().equals("owner"))
               .flatMap(attribute -> Stream.of(attribute.getValues()))
               .filter(value -> value.startsWith("uid=" + sender.asString()))
               .map(Throwing.function(this::resolveUserMail))
               .flatMap(Collection::stream)
               .anyMatch(sender.asOptional().get()::equals);
           Supplier<Boolean> isOwner = () -> list.getAttributes().stream()
               .filter(attribute -> attribute.getName().equals("owner"))
               .flatMap(attribute -> Stream.of(attribute.getValues()))
               .map(Throwing.function(this::resolveUserMail))
               .flatMap(Collection::stream)
               .anyMatch(sender.asOptional().get()::equals);

           return isOwnerUidHeuristic.get() || isOwner.get();
       };
   }

   SenderValidationPolicy domainRestrictedValidationPolicy() {
       return (sender, list) -> {
           if (sender.isNullSender()) {
               return false;
           }

           try {
               MailAddress listAddress = new MailAddress(list.getAttributeValue(mailAttributeForGroups));
               if (sender.get().getDomain().equals(listAddress.getDomain())) {
                   return true;
               }
               if (listAddress.getDomain().asString().startsWith("lists.")) {
                   Domain baseDomain = Domain.of(listAddress.getDomain().asString().substring(6));
                   return sender.get().getDomain().equals(baseDomain);
               }
               return false;
           } catch (AddressException e) {
               throw new RuntimeException(e);
           }
       };
   }

    SenderValidationPolicy chooseSenderValidationPolicy(SearchResultEntry list) {
        Attribute businessCategory = list.getAttribute("businessCategory");
        if (businessCategory == null) {
            return SenderValidationPolicy.OPEN;
        }
        return switch (extractBusinessCategory(businessCategory.getValue().toLowerCase().trim())) {
            case "openlist" -> SenderValidationPolicy.OPEN;
            case "internallist" -> SenderValidationPolicy.INTERNAL.apply(getMailetContext());
            case "memberrestrictedlist" -> memberSenderValidationPolicy();
            case "ownerrestrictedlist" -> ownerSenderValidationPolicy();
            case "domainrestrictedlist" -> domainRestrictedValidationPolicy();
            default -> SenderValidationPolicy.OPEN;
        };
    }

    private String extractBusinessCategory(String ldapValue) {
        if (ldapValue.contains(",")) {
            try {
                return Arrays.stream(new DN(ldapValue).getRDNs())
                    .flatMap(rdn -> rdn.getNameValuePairs().stream())
                    .filter(pair -> pair.getAttributeName().equals("cn"))
                    .findFirst()
                    .map(RDNNameValuePair::getAttributeValue)
                    .orElse(ldapValue);
            } catch (LDAPException e) {
                LOGGER.info("Non DN value '{}' for businessCategory contains coma", ldapValue);
                return ldapValue;
            }
        }
        return ldapValue;
    }
}
