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

package com.linagora.tmail.matcher;

import java.io.UnsupportedEncodingException;
import java.util.Collection;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * IsMainRecipient matcher returns only the recipients of the Mail that are explicitly present in
 * the To header.
 *
 * <p>If the mail has no or empty "To" header, this matcher returns an
 * empty collection (no recipients matched).</p>
 */
public class IsMainRecipient extends GenericMatcher {

    @Override
    public String getMatcherName() {
        return "IsMainRecipient";
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        MimeMessage mimeMessage = mail.getMessage();
        Collection<MailAddress> toAddresses = getMailAddressesFromHeader(mimeMessage, "To");
        Collection<MailAddress> recipients = mail.getRecipients();

        ImmutableList.Builder<MailAddress> mainRecipients = ImmutableList.builder();
        for (MailAddress recipient : recipients) {
            if (toAddresses.contains(recipient)) {
                mainRecipients.add(recipient);
            }
        }
        return mainRecipients.build();
    }

    private Collection<MailAddress> getMailAddressesFromHeader(MimeMessage message, String headerName) throws MessagingException {
        String[] headers = message.getHeader(headerName);
        ImmutableList.Builder<MailAddress> addresses = ImmutableList.builder();
        if (headers != null) {
            for (String header : headers) {
                addresses.addAll(getMailAddressesFromHeaderLine(header));
            }
        }
        return addresses.build();
    }

    private ImmutableList<MailAddress> getMailAddressesFromHeaderLine(String header) throws MessagingException {
        String unfoldedDecodedString = sanitizeHeaderString(header);
        Iterable<String> headerParts = Splitter.on(",")
            .split(unfoldedDecodedString);
        return getMailAddressesFromHeadersParts(headerParts);
    }

    private ImmutableList<MailAddress> getMailAddressesFromHeadersParts(Iterable<String> headerParts) {
        ImmutableList.Builder<MailAddress> result = ImmutableList.builder();
        for (String headerPart : headerParts) {
            result.addAll(asMailAddresses(headerPart));
        }
        return result.build();
    }

    private Collection<MailAddress> asMailAddresses(String headerPart) {
        return LenientAddressParser.DEFAULT
            .parseAddressList(MimeUtil.unfold(headerPart))
            .flatten()
            .stream()
            .map(this::asMailAddress)
            .collect(ImmutableList.toImmutableList());
    }

    private MailAddress asMailAddress(Mailbox mailbox) {
        try {
            return new MailAddress(mailbox.getAddress());
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }

    private String sanitizeHeaderString(String header) throws MessagingException {
        try {
            return MimeUtility.unfold(MimeUtility.decodeText(header));
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("Can not decode header", e);
        }
    }
}
