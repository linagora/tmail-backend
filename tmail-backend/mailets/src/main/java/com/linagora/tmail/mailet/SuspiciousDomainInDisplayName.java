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
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * Matcher detecting a local domain referenced inside the From header display name.
 *
 * <p>An attacker may include a local domain in their display name (bare or as part of an email
 * address) to fool the recipient into thinking the message originates from inside the
 * organisation. This matcher flags such emails so they can be quarantined or labelled.</p>
 *
 * <p>Examples that match when {@code linagora.com} is a local domain:</p>
 * <ul>
 *   <li>{@code "John from linagora.com" <attacker@evil.org>} — bare domain</li>
 *   <li>{@code "john@linagora.com" <attacker@evil.org>} — domain inside email address</li>
 *   <li>{@code "John (linagora.com)" <attacker@evil.org>} — domain in parentheses</li>
 * </ul>
 *
 * <p>No configuration parameter is required:</p>
 * <pre><code>
 * &lt;mailet matcher="SuspiciousDomainInDisplayName" class="AddHeader"&gt;
 *   &lt;name&gt;X-Suspicious-Domain&lt;/name&gt;&lt;value&gt;true&lt;/value&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class SuspiciousDomainInDisplayName extends GenericMatcher {
    // Split on whitespace, common punctuation, and @ (to separate local-part from domain in
    // email-like patterns). Dots are NOT separators — they must stay within domain tokens.
    // trimResults strips leading/trailing dots and commas left on token edges (e.g. "linagora.com,").
    private static final CharMatcher SEPARATORS =
        CharMatcher.anyOf("()[]{},<>:;@").or(CharMatcher.whitespace());
    private static final Splitter TOKEN_SPLITTER = Splitter
        .on(SEPARATORS)
        .trimResults(CharMatcher.anyOf(".,"))
        .omitEmptyStrings();

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        Optional<Mailbox> fromMailbox = extractFromMailbox(mail);

        Optional<Domain> fromAddressDomain = fromMailbox
            .map(Mailbox::getDomain)
            .filter(domain -> domain != null && !domain.isBlank())
            .flatMap(this::tryParseDomain);

        boolean suspicious = fromMailbox
            .map(Mailbox::getName)
            .filter(name -> name != null && !name.isBlank())
            .map(this::extractDomains)
            .orElse(Stream.empty())
            .filter(domain -> fromAddressDomain.map(fromDomain -> !fromDomain.equals(domain)).orElse(true))
            .anyMatch(domain -> getMailetContext().isLocalServer(domain));

        return Optional.of(mail.getRecipients())
            .filter(recipients -> suspicious)
            .orElse(ImmutableList.of());
    }

    private Optional<Mailbox> extractFromMailbox(Mail mail) throws MessagingException {
        String[] fromHeaders = mail.getMessage().getHeader("From");
        if (fromHeaders == null || fromHeaders.length == 0) {
            return Optional.empty();
        }
        return LenientAddressParser.DEFAULT
            .parseAddressList(fromHeaders[0])
            .flatten()
            .stream()
            .findFirst();
    }

    private Stream<Domain> extractDomains(String displayName) {
        return TOKEN_SPLITTER.splitToStream(displayName)
            .filter(token -> token.contains("."))
            .flatMap(token -> tryParseDomain(token).stream());
    }

    private Optional<Domain> tryParseDomain(String value) {
        try {
            return Optional.of(Domain.of(value));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
