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

package org.apache.james.transport.matchers;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * Base class for matchers counting how many times a mail transited via a given host name or IP,
 * as recorded in its <code>Received</code> headers.
 *
 * <p>The host name or IP is looked up as a whole token (case insensitive), meaning that
 * <code>smtp.twake.app</code> does not match <code>smtp.twake.application</code> nor
 * <code>relay.smtp.twake.app</code>.</p>
 */
public abstract class AbstractTransitedVia extends GenericMatcher {
    private static final String RECEIVED = "Received";
    private static final String HOST_CHARACTER = "[\\p{Alnum}\\-:_]";

    private final long minimumTransitCount;
    private Pattern nameOrIpPattern;

    protected AbstractTransitedVia(long minimumTransitCount) {
        this.minimumTransitCount = minimumTransitCount;
    }

    @Override
    public void init() throws MessagingException {
        String nameOrIp = Optional.ofNullable(getCondition())
            .map(String::trim)
            .filter(condition -> !Strings.isNullOrEmpty(condition))
            .orElseThrow(() -> new MessagingException(getMatcherName() + " expects a host name or an IP as a condition"));

        nameOrIpPattern = asTokenPattern(nameOrIp);
    }

    private static Pattern asTokenPattern(String nameOrIp) {
        String notPrecededByHostCharacters = "(?<!" + HOST_CHARACTER + "|\\.)";
        String notFollowedByHostCharacters = "(?!" + HOST_CHARACTER + "|\\." + HOST_CHARACTER + ")";
        return Pattern.compile(notPrecededByHostCharacters + Pattern.quote(nameOrIp) + notFollowedByHostCharacters,
            Pattern.CASE_INSENSITIVE);
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (transitCount(mail) >= minimumTransitCount) {
            return mail.getRecipients();
        }
        return ImmutableList.of();
    }

    private long transitCount(Mail mail) throws MessagingException {
        return Optional.ofNullable(mail.getMessage().getHeader(RECEIVED))
            .stream()
            .flatMap(Arrays::stream)
            .filter(receivedHeader -> nameOrIpPattern.matcher(receivedHeader).find())
            .count();
    }
}
