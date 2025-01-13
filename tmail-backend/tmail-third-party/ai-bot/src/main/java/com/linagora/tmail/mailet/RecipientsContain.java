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
import org.apache.james.transport.matchers.utils.MailAddressCollectionReader;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * RecipientsContain is a matcher that checks if the recipients of an email
 * include all the addresses specified in the match condition.
 *
 * <p>
 * Configuration example:
 * </p>
 *
 * <pre>
 * {@code
 * <mailet match="com.linagora.tmail.mailet.RecipientsContain=recipient1@example.com,recipient2@example.com" class="org.apache.james.transport.mailets.Null"/>
 * }
 * </pre>
 *
 * <p>
 * This matcher will return all recipients of the email if all specified addresses
 * are present among the recipients. Otherwise, it returns an empty list.
 * </p>
 */
public class RecipientsContain extends GenericMatcher {
    private Collection<MailAddress> recipientsCondition;

    @Override
    public void init() throws MessagingException {
        if (Strings.isNullOrEmpty(getCondition())) {
            throw new MessagingException("RecipientsContain should have a condition composed of a list of mail addresses");
        }

        recipientsCondition = MailAddressCollectionReader.read(getCondition())
            .stream()
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        if (recipientsCondition.isEmpty()) {
            throw new MessagingException("RecipientsContain should have at least one address passed as a condition");
        }
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (mail.getRecipients().containsAll(recipientsCondition)) {
            return mail.getRecipients();
        }
        return ImmutableList.of();
    }

    @Override
    public String getMatcherName() {
        return "RecipientsContain";
    }
}
