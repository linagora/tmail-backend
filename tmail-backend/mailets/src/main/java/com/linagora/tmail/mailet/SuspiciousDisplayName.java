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
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

/**
 * Matcher detecting display name usurpation: flags emails whose From header display name
 * matches a local user's LDAP displayName, suggesting identity spoofing by an external sender.
 *
 * <p>The display name is tokenized (punctuation stripped, stop words removed, tokens &lt; 3 chars
 * ignored) and an AND substring search is performed against the LDAP {@code displayName} attribute.
 * Because the filter applies to a single LDAP entry, partial combinations like "Alice Doe" or
 * "John Martin" do not match a user named "John Doe".</p>
 *
 * <p>Configuration:</p>
 * <pre><code>
 * &lt;!-- userBase from LdapRepositoryConfiguration, default stop words --&gt;
 * &lt;mailet matcher="SuspiciousDisplayName" class="AddHeader"&gt;
 *   &lt;name&gt;X-Suspicious-DisplayName&lt;/name&gt;&lt;value&gt;true&lt;/value&gt;
 * &lt;/mailet&gt;
 *
 * &lt;!-- explicit userBase --&gt;
 * &lt;mailet matcher="SuspiciousDisplayName=ou=people,dc=james,dc=org" class="AddHeader"&gt; ... &lt;/mailet&gt;
 *
 * &lt;!-- explicit userBase + custom stop words --&gt;
 * &lt;mailet matcher="SuspiciousDisplayName=ou=people,dc=james,dc=org?stopWords=Mr,Mme,Mrs,Dr" class="AddHeader"&gt;
 *   ...
 * &lt;/mailet&gt;
 * </code></pre>
 *
 * <p>Default stop words: Mr, Mme, Mrs, Ms, Dr, M, Me, Prof</p>
 */
public class SuspiciousDisplayName extends GenericMatcher {
    private static final String DISPLAY_NAME_ATTRIBUTE = "displayName";
    private static final ImmutableSet<String> DEFAULT_STOP_WORDS =
        ImmutableSet.of("mr", "mme", "mrs", "ms", "dr", "m", "me", "prof");
    private static final Pattern PUNCTUATION = Pattern.compile("[()\\[\\]{}.,<>:;]");

    private final LDAPConnectionPool ldapConnectionPool;
    private final LdapRepositoryConfiguration configuration;
    private String userBase;
    private ImmutableSet<String> stopWords;

    @Inject
    public SuspiciousDisplayName(LDAPConnectionPool ldapConnectionPool,
                                  LdapRepositoryConfiguration configuration) {
        this.ldapConnectionPool = ldapConnectionPool;
        this.configuration = configuration;
    }

    @VisibleForTesting
    public SuspiciousDisplayName(LdapRepositoryConfiguration configuration) throws LDAPException {
        this(new LDAPConnectionFactory(configuration).getLdapConnectionPool(), configuration);
    }

    @Override
    public void init() throws MessagingException {
        String condition = getCondition() != null ? getCondition().trim() : "";
        int queryStart = condition.indexOf('?');

        String basePart = (queryStart == -1 ? condition : condition.substring(0, queryStart)).trim();
        userBase = basePart.isEmpty() ? configuration.getUserBase() : basePart;

        stopWords = parseStopWords(queryStart == -1 ? "" : condition.substring(queryStart + 1));
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        List<String> tokens = extractFromDisplayName(mail)
            .map(this::tokenize)
            .orElse(ImmutableList.of());

        if (tokens.isEmpty()) {
            return ImmutableList.of();
        }
        if (isSuspicious(tokens, mail.getMaybeSender().asOptional())) {
            return mail.getRecipients();
        }
        return ImmutableList.of();
    }

    private Optional<String> extractFromDisplayName(Mail mail) throws MessagingException {
        String[] fromHeaders = mail.getMessage().getHeader("From");
        if (fromHeaders == null || fromHeaders.length == 0) {
            return Optional.empty();
        }
        return LenientAddressParser.DEFAULT
            .parseAddressList(fromHeaders[0])
            .flatten()
            .stream()
            .findFirst()
            .map(mailbox -> mailbox.getName())
            .filter(name -> name != null && !name.isBlank());
    }

    private List<String> tokenize(String displayName) {
        return Splitter.on(' ').trimResults().omitEmptyStrings()
            .splitToStream(PUNCTUATION.matcher(displayName).replaceAll(" "))
            .filter(token -> token.length() >= 3)
            .filter(token -> !stopWords.contains(token.toLowerCase(Locale.ROOT)))
            .collect(ImmutableList.toImmutableList());
    }

    private boolean isSuspicious(List<String> tokens, Optional<MailAddress> sender) {
        try {
            Filter[] subFilters = tokens.stream()
                .map(token -> Filter.createSubstringFilter(
                    DISPLAY_NAME_ATTRIBUTE, null, new String[]{token}, null))
                .toArray(Filter[]::new);

            Filter filter = subFilters.length == 1
                ? subFilters[0]
                : Filter.createANDFilter(subFilters);

            List<SearchResultEntry> entries = ldapConnectionPool
                .search(userBase, SearchScope.SUB, filter,
                    DISPLAY_NAME_ATTRIBUTE, configuration.getUserIdAttribute())
                .getSearchEntries();

            if (entries.isEmpty()) {
                return false;
            }
            // Not suspicious if the sender is the actual LDAP user whose displayName matched
            return sender
                .map(senderAddress -> entries.stream()
                    .noneMatch(entry -> senderMatchesEntry(senderAddress, entry)))
                .orElse(true);
        } catch (LDAPSearchException e) {
            throw new RuntimeException("Failed searching LDAP for suspicious display name", e);
        }
    }

    private boolean senderMatchesEntry(MailAddress sender, SearchResultEntry entry) {
        return Optional.ofNullable(entry.getAttributeValue(configuration.getUserIdAttribute()))
            .map(value -> value.equalsIgnoreCase(sender.asString()))
            .orElse(false);
    }

    private ImmutableSet<String> parseStopWords(String queryString) {
        return Splitter.on('&').trimResults().omitEmptyStrings()
            .splitToStream(queryString)
            .filter(pair -> pair.toLowerCase(Locale.ROOT).startsWith("stopwords="))
            .findFirst()
            .map(pair -> pair.substring(pair.indexOf('=') + 1))
            .filter(csv -> !csv.isBlank())
            .map(csv -> Splitter.on(',').trimResults().omitEmptyStrings()
                .splitToStream(csv)
                .map(w -> w.toLowerCase(Locale.ROOT))
                .collect(ImmutableSet.toImmutableSet()))
            .orElse(DEFAULT_STOP_WORDS);
    }
}
