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

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.util.StreamUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * AlignFromHeaderWithMailFrom rewrites the From header to align with the MAIL FROM address.
 * This is useful for email forwarding to preserve DKIM alignment.
 *
 * <p>The mailet performs the following transformations:</p>
 * <ul>
 *     <li>Stores the original From header in an "Original-From" header</li>
 *     <li>Rewrites the From header to use the MAIL FROM address</li>
 *     <li>Preserves the original sender information in the display name</li>
 * </ul>
 *
 * <p>Example transformation:</p>
 * <pre>
 * From: "Bob External" &lt;bob@external.com&gt;
 * MAIL FROM: alice@us.com
 *
 * Becomes:
 * From: "Bob External (bob@external.com)" &lt;alice@us.com&gt;
 * Original-From: bob@external.com
 * </pre>
 *
 * <p>Configuration parameters:</p>
 * <ul>
 *     <li><b>misAlignProcessor</b> (optional): The processor to which mails with unparseable From headers
 *         or no From header will be redirected. Defaults to "error".</li>
 * </ul>
 *
 * <p>Mails with missing or unparseable From headers are redirected to the configured processor
 * (via misAlignProcessor parameter) for error handling.</p>
 *
 * <p>To strip existing DKIM signatures (recommended for forwarding), use RemoveMimeHeader before this mailet:</p>
 * <pre><code>
 * &lt;mailet match="All" class="RemoveMimeHeader"&gt;
 *   &lt;name&gt;DKIM-Signature&lt;/name&gt;
 * &lt;/mailet&gt;
 * &lt;mailet match="All" class="AlignFromHeaderWithMailFrom"&gt;
 *   &lt;misAlignProcessor&gt;error&lt;/misAlignProcessor&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class AlignFromHeaderWithMailFrom extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlignFromHeaderWithMailFrom.class);

    private static final String FROM_HEADER = "From";
    private static final String ORIGINAL_FROM_HEADER = "Original-From";

    private String misAlignProcessor;

    @Override
    public void init() throws MessagingException {
        misAlignProcessor = getInitParameter("misAlignProcessor", Mail.ERROR);
    }

    @Override
    public String getMailetInfo() {
        return "AlignFromHeaderWithMailFrom Mailet";
    }

    @Override
    public Collection<ProcessingState> requiredProcessingState() {
        return ImmutableList.of(new ProcessingState(misAlignProcessor));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        Optional<Mailbox> maybeOriginalFrom = retrieveOriginalFrom(mail);

        if (!maybeOriginalFrom.isPresent()) {
            LOGGER.warn("Could not parse From header for mail {}, skipping alignment", mail.getName());
            mail.setState(misAlignProcessor);
            return;
        }

        Mailbox originalFrom = maybeOriginalFrom.get();

        mail.getMaybeSender()
            .asOptional()
            .filter(mailFromAddress -> !originalFrom.getAddress().equalsIgnoreCase(mailFromAddress.asString()))
            .ifPresent(Throwing.consumer(mailFromAddress -> reAlignMessage(mail, originalFrom, mailFromAddress)));
    }

    private static Optional<Mailbox> retrieveOriginalFrom(Mail mail) throws MessagingException {
        return Optional.ofNullable(mail.getMessage().getHeader(FROM_HEADER))
            .filter(headers -> headers.length > 0)
            .flatMap(AlignFromHeaderWithMailFrom::retrieveFirstMailbox);
    }

    private static Optional<Mailbox> retrieveFirstMailbox(String[] fromHeaders) {
        return StreamUtils.ofNullables(fromHeaders)
            .flatMap(headerLine -> LenientAddressParser.DEFAULT
                .parseAddressList(headerLine)
                .flatten()
                .stream())
            .findFirst();
    }

    private void reAlignMessage(Mail mail, Mailbox originalFrom, MailAddress mailFromAddress) throws MessagingException {
        mail.getMessage().setHeader(ORIGINAL_FROM_HEADER, originalFrom.getAddress());
        String newDisplayName = buildNewDisplayName(originalFrom);
        String newFromHeader = formatFromHeader(newDisplayName, mailFromAddress.asString());

        mail.getMessage().setHeader(FROM_HEADER, newFromHeader);
        mail.getMessage().saveChanges();

        LOGGER.info("Aligned From header for mail {}: {} -> {}", mail.getName(), originalFrom.getAddress(), mailFromAddress.asString());
    }

    private String buildNewDisplayName(Mailbox originalFrom) {
        if (Strings.isNullOrEmpty(originalFrom.getName())) {
            return String.format("(%s)", originalFrom.getAddress());
        }
        return String.format("%s (%s)", originalFrom.getName(), originalFrom.getAddress());
    }

    private String formatFromHeader(String displayName, String emailAddress) {
        int atIndex = emailAddress.indexOf('@');
        if (atIndex == -1) {
            return emailAddress;
        }

        String localPart = emailAddress.substring(0, atIndex);
        String domain = emailAddress.substring(atIndex + 1);

        return Fields.from(asMailbox(displayName, localPart, domain)).getBody();
    }

    private static Mailbox asMailbox(String displayName, String localPart, String domain) {
        if (Strings.isNullOrEmpty(displayName)) {
            return new Mailbox(localPart, domain);
        }
        return new Mailbox(displayName, localPart, domain);
    }
}
