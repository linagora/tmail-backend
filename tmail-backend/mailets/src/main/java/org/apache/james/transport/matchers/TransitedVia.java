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
 * Matches mails that already transited via the given host name or IP, that is to say
 * mails having at least one <code>Received</code> header mentioning it.
 *
 * <p>Configuration example:</p>
 * <pre>
 * &lt;mailet match="TransitedVia=smtp.twake.app" class="..."&gt;
 *    ...
 * &lt;/mailet&gt;
 * </pre>
 *
 * @see TransitedTwiceVia
 */
public class TransitedVia extends AbstractTransitedVia {
    public TransitedVia() {
        super(1);
    }
}
