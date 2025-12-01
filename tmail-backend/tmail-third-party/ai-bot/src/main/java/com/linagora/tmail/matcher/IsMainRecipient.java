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

import java.util.Arrays;
import java.util.Collection;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

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

        return mail.getRecipients()
            .stream()
            .filter(toAddresses::contains)
            .collect(ImmutableList.toImmutableList());
    }

    private Collection<MailAddress> getMailAddressesFromHeader(MimeMessage message, String headerName) throws MessagingException {
        String[] headers = message.getHeader(headerName);
        ImmutableList.Builder<MailAddress> addresses = ImmutableList.builder();
        if (headers != null) {
            Arrays.stream(headers)
                .forEach(header -> addresses.addAll(getMailAddressesFromHeader(header)));
        }
        return addresses.build();
    }

    private Collection<MailAddress> getMailAddressesFromHeader(String header) {
        return asMailAddresses(header);
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

}
