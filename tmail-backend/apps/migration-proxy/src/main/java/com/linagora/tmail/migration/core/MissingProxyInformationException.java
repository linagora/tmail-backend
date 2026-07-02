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

package com.linagora.tmail.migration.core;

/**
 * Raised when a backend is configured with {@code forwardProxyInfo=true} but the inbound connection
 * did not carry any PROXY protocol information to forward (the inbound proxy protocol is not active).
 */
public class MissingProxyInformationException extends RuntimeException {
    public MissingProxyInformationException(Backend backend) {
        super("Backend " + backend.name() + " requires forwarding proxy information, but the inbound "
            + "connection carries none (enable the inbound proxy protocol or set forwardProxyInfo=false)");
    }
}
