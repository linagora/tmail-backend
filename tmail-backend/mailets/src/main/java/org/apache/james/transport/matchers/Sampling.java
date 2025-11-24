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

import java.util.Collection;
import java.util.Random;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.collect.ImmutableList;

/**
 * Matcher that randomly matches a percentage of emails based on a probability parameter.
 *
 * <p>This matcher is useful for:</p>
 * <ul>
 *   <li>Traffic distribution: Evenly divide outgoing mail across multiple gateways to enhance email deliverability rates</li>
 *   <li>Reputation management: Enable gradual warming up of sending reputation through controlled traffic distribution</li>
 * </ul>
 *
 * <p>Configuration example:</p>
 * <pre>
 * &lt;mailet match="Sampling=0.01" class="..."&gt;
 *    ...
 * &lt;/mailet&gt;
 * </pre>
 *
 * <p>The parameter is a decimal value between 0.0 and 1.0 representing the probability of matching.
 * For example, 0.01 means 1% of emails will be randomly matched.</p>
 */
public class Sampling extends GenericMatcher {

    private static final Random RANDOM = new Random();

    private double samplingRate;

    @Override
    public void init() throws MessagingException {
        String condition = getCondition();

        if (condition == null || condition.trim().isEmpty()) {
            throw new MessagingException("Sampling rate must be specified");
        }

        samplingRate = Double.parseDouble(condition.trim());

        if (samplingRate < 0.0 || samplingRate > 1.0) {
            throw new MessagingException("Sampling rate must be between 0.0 and 1.0, got: " + samplingRate);
        }
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (RANDOM.nextDouble() < samplingRate) {
            return mail.getRecipients();
        }
        return ImmutableList.of();
    }
}
