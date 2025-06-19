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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.util.AuditTrail;
import org.apache.mailet.LoopPrevention;
import org.apache.mailet.Mail;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

/**
 * <p>Mailing list resolution for Twake mail based on LDAP groups. Follows OBM pattern.</p>
 *
 * <p>This mailet will lookup mailAdress in order to identify groups and substitute their mail address with the ones of users.</p>
 *
 * <ul>Configuration:
 *  <li>baseDN: the base DN to use within group search. EG: ou=lists,dc=linagora,dc=com</li>
 *  <li>rejectedSenderProcessor: processor to handle rejected sender. Generally this is comprised of a bounce mailet explaining the
 *  rejection.</li>
 *  <li>mailAttributeForGroups: Attribute holding the mail address of a group. For easy testing this can be set to description
 *  but for production use a special LDAP schema needs to be crafted for using the mail attribute.</li>
 *  </ul>
 *
 *  <p>Sample structure for LDAP groups:</p>
 *
 *  <pre><code>dn: cn=all_linagora,ou=groups,dc=linagora.com,dc=lng
 * objectClass: posixGroup
 * objectClass: obmGroup
 * cn: all_linagora
 * gidNumber: 13439
 * description: all linagora
 * mailAccess: PERMIT
 * mail: xxx@linagora.com
 * obmDomain: linagora.com
 * memberUid: pxxx
 * memberUid: ixxx
 * member: uid=pxxx,ou=users,dc=linagora.com,dc=lng
 * member: uid=ixxx,ou=users,dc=linagora.com,dc=lng
 * mailBox: pxxx@linagora.com
 * mailBox: ixxx@linagora.com
 * </code></pre>
 */
public class OBMLDAPMailingList extends GenericMailet {
    record GroupResolutionResult(MailAddress listAddress, boolean isPublic, List<MailAddress> members) {

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
        Function<Collection<MailAddress>, MailTransformation> removeRecipients = rcpts -> mail -> {
            Set<MailAddress> toBeRemoved = Sets.intersection(
                ImmutableSet.copyOf(rcpts),
                ImmutableSet.copyOf(mail.getRecipients()));

            mail.setRecipients(mail.getRecipients()
                .stream()
                .filter(r -> !toBeRemoved.contains(r))
                .collect(ImmutableList.toImmutableList()));
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

    private final LDAPConnectionPool ldapConnectionPool;
    private Filter objectClassFilter;
    private String[] listAttributes;
    private String baseDN;
    private String rejectedSenderProcessor;
    private String mailAttributeForGroups;

    @Inject
    public OBMLDAPMailingList(LDAPConnectionPool ldapConnectionPool) {
        this.ldapConnectionPool = ldapConnectionPool;
    }

    @VisibleForTesting
    public OBMLDAPMailingList(LdapRepositoryConfiguration configuration) throws LDAPException {
        this(new LDAPConnectionFactory(configuration).getLdapConnectionPool());
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        mail.getRecipients()
            .stream()
            .filter(recipient -> getMailetContext().isLocalServer(recipient.getDomain()))
            .flatMap(Throwing.function(list -> resolveListDN(list).stream()))
            .map(Throwing.function(this::asGroupResolutionResult))
            .flatMap(Optional::stream)
            .map(entry -> listToMailTransformation(mail.getMaybeSender(), entry))
            .reduce(MailTransformation.NOOP, MailTransformation::doCompose)
            .transform(mail);
    }

    @Override
    public void init() throws MessagingException {
        baseDN = getInitParameter("baseDN");
        mailAttributeForGroups = getInitParameter("mailAttributeForGroups", "mail");
        String groupObjectClass = getInitParameter("groupObjectClass", "obmGroup");
        objectClassFilter = Filter.createEqualityFilter("objectClass", groupObjectClass);
        rejectedSenderProcessor = getInitParameter("rejectedSenderProcessor");

        this.listAttributes = ImmutableSet.builder()
            .add(mailAttributeForGroups)
            .add("mailBox")
            .add("mailAccess")
            .build().toArray(String[]::new);
    }

    private Optional<GroupResolutionResult> asGroupResolutionResult(SearchResultEntry entry) throws AddressException {
        if (entry == null) {
            return Optional.empty();
        }
        boolean isPublic = isPublic(entry);
        if (entry.hasAttribute("mailBox")) {
            return Optional.of(
                new GroupResolutionResult(
                    new MailAddress(entry.getAttributeValue(mailAttributeForGroups)),
                    isPublic,
                    Arrays.stream(entry.getAttribute("mailBox").getValues())
                        .map(Throwing.function(MailAddress::new))
                        .collect(ImmutableList.toImmutableList())));
        }
        return Optional.of(
            new GroupResolutionResult(
                new MailAddress(entry.getAttributeValue(mailAttributeForGroups)),
                isPublic,
                ImmutableList.of()));
    }

    private static boolean isPublic(SearchResultEntry entry) {
        if (entry.hasAttribute("mailAccess")) {
            return entry.getAttribute("mailAccess").getValue().equalsIgnoreCase("PERMIT");
        }
        return true;
    }

    @Override
    public Collection<ProcessingState> requiredProcessingState() {
        return ImmutableList.of(new ProcessingState(rejectedSenderProcessor));
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
        return Filter.createANDFilter(objectClassFilter, specificUserFilter);
    }

    private MailTransformation listToMailTransformation(MaybeSender maybeSender, GroupResolutionResult list) {
        MailAddress listAddress = list.listAddress();

        boolean authorized = list.isPublic()
            || maybeSender.isNullSender()
            || list.members().contains(maybeSender.get());

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

        List<MailAddress> memberAddresses = list.members();

        MailTransformation sendEmailToGroup = mail -> {
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
        };
        return MailTransformation.removeRecipient.apply(listAddress)
            .doComposeIf(
                sendEmailToGroup.doCompose(MailTransformation.removeRecipients.apply(memberAddresses)),
                mail -> !LoopPrevention.RecordedRecipients.fromMail(mail).getRecipients().contains(listAddress));
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
}
