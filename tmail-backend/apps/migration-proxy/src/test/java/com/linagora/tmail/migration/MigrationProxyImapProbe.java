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

package com.linagora.tmail.migration;

import java.net.InetSocketAddress;

import jakarta.inject.Inject;

import org.apache.james.imapserver.netty.IMAPServerFactory;
import org.apache.james.utils.GuiceProbe;

import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;

class MigrationProxyImapProbe implements GuiceProbe {
    static final Module MODULE = binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
        .addBinding()
        .to(MigrationProxyImapProbe.class);

    private final IMAPServerFactory imapServerFactory;

    @Inject
    MigrationProxyImapProbe(IMAPServerFactory imapServerFactory) {
        this.imapServerFactory = imapServerFactory;
    }

    /**
     * The first listener of imapserver.xml: clear text, which allows clear-text authentication.
     */
    int getImapPort() {
        return listenerPort(0);
    }

    /**
     * The second listener of imapserver.xml: STARTTLS, with the default policy that requires it before
     * any password is sent.
     */
    int getStartTlsImapPort() {
        return listenerPort(1);
    }

    int getPlainDisabledImapPort() {
        return listenerPort(2);
    }

    private int listenerPort(int index) {
        return imapServerFactory.getServers().stream()
            .skip(index)
            .flatMap(server -> server.getListenAddresses().stream())
            .findFirst()
            .map(InetSocketAddress::getPort)
            .orElseThrow(() -> new IllegalStateException("IMAP server " + index + " not defined"));
    }
}
