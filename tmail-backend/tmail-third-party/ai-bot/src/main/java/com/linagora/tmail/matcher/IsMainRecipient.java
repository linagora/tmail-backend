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

import java.util.Collection;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.util.StreamUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * IsMainRecipient matcher returns only the recipients of the Mail that are explicitly present in
 * the To header.
 *
 * <p>If the mail has no or empty "To" header, this matcher returns an
 * empty collection (no recipients matched).</p>
 */
public class IsMainRecipient extends GenericMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(IsMainRecipient.class);

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
        return StreamUtils.ofNullable(message.getHeader(headerName))
            .flatMap(header -> asMailAddresses(header).stream())
            .collect(ImmutableSet.toImmutableSet());
    }

    private Collection<MailAddress> asMailAddresses(String headerPart) {
        return LenientAddressParser.DEFAULT
            .parseAddressList(headerPart)
            .flatten()
            .stream()
            .flatMap(this::asMailAddress)
            .collect(ImmutableList.toImmutableList());
    }

    private Stream<MailAddress> asMailAddress(Mailbox mailbox) {
        try {
            return Stream.of(new MailAddress(mailbox.getAddress()));
        } catch (AddressException e) {
            LOGGER.warn("Invalid mail address: {}", mailbox.getAddress(), e);
            return Stream.empty();
        }
    }

}
