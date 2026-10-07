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

/**
 * Matches mails that transited at least twice via the given host name or IP, that is to say
 * mails having at least two <code>Received</code> headers mentioning it.
 *
 * <p>Useful for relay loop detection: the <code>Received</code> header added upon reception by
 * the server itself already counts as one transit.</p>
 *
 * <p>Configuration example:</p>
 * <pre>
 * &lt;mailet match="TransitedTwiceVia=smtp.twake.app" class="ToProcessor"&gt;
 *    &lt;processor&gt;relay-loop&lt;/processor&gt;
 * &lt;/mailet&gt;
 * </pre>
 *
 * @see TransitedVia
 */
public class TransitedTwiceVia extends AbstractTransitedVia {
    public TransitedTwiceVia() {
        super(2);
    }
}
