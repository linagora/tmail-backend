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

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.collect.ImmutableList;

/**
 * Matches DMARC aggregate report emails that contain at least one authentication failure.
 *
 * <p>A failure is defined as a {@code <record>} element where both
 * {@code policy_evaluated/dkim} and {@code policy_evaluated/spf} are {@code fail}.
 * This is the unambiguous signal that a message from that source was unauthenticated
 * (DMARC passes when either alignment check passes).</p>
 *
 * <p>Handles ZIP, GZIP and plain XML attachments. Protects against XXE and zip-bomb
 * attacks. Returns no match if the email is not a recognizable DMARC report or if
 * the attachment cannot be parsed.</p>
 *
 * <p>Example usage:</p>
 * <pre><code>
 * &lt;mailet match="IsDmarcReportWithIssues" class="ToRepository"&gt;
 *   &lt;repositoryPath&gt;var://storage/inbox-dmarc-issues&lt;/repositoryPath&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class IsDmarcReportWithIssues extends GenericMatcher {
    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        return DmarcReportAnalyzer.analyze(mail)
            .filter(hasIssues -> hasIssues)
            .map(ignored -> mail.getRecipients())
            .orElse(ImmutableList.of());
    }
}
