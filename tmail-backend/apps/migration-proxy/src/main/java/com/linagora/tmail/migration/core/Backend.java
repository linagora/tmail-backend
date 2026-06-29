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

import org.apache.james.util.Host;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

/**
 * A target the proxy can relay a connection to.
 *
 * @param name             identifies the backend in metrics (typically {@code old} or {@code new})
 * @param host             host and port of the backend
 * @param ssl              when true the proxy opens an implicit TLS connection towards the backend
 * @param sslIgnoreCertificates when true backend certificates are not validated (self-signed certs)
 * @param forwardProxyInfo when true the proxy forwards the PROXY protocol information it received from
 *                         the client connection (requires the inbound proxy protocol to be active),
 *                         relaying the original client address to the backend
 */
public record Backend(String name, Host host, boolean ssl, boolean sslIgnoreCertificates, boolean forwardProxyInfo) {
    public Backend {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(host);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .add("host", host.getHostName())
            .add("port", host.getPort())
            .add("ssl", ssl)
            .add("sslIgnoreCertificates", sslIgnoreCertificates)
            .add("forwardProxyInfo", forwardProxyInfo)
            .toString();
    }
}
