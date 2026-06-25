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
 * Matches DMARC aggregate report emails where all records pass authentication checks.
 *
 * <p>A report has no issues when no {@code <record>} element has both
 * {@code policy_evaluated/dkim} and {@code policy_evaluated/spf} set to {@code fail}.
 * Reports with a mix of passing and failing records do NOT match this matcher.</p>
 *
 * <p>Returns no match if the email is not a recognizable DMARC report, or if
 * the attachment cannot be parsed (fail-safe: unclassified reports stay in INBOX).</p>
 *
 * <p>Example usage (archive clean reports, leave problematic ones in INBOX):</p>
 * <pre><code>
 * &lt;mailet match="And=(RecipientIs=dmarc-reports@example.com, IsDmarcReportWithoutIssues)"
 *         class="ToRepository"&gt;
 *   &lt;repositoryPath&gt;var://storage/archive&lt;/repositoryPath&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class IsDmarcReportWithoutIssues extends GenericMatcher {
    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        return DmarcReportAnalyzer.analyze(mail)
            .filter(hasIssues -> !hasIssues)
            .map(ignored -> mail.getRecipients())
            .orElse(ImmutableList.of());
    }
}
