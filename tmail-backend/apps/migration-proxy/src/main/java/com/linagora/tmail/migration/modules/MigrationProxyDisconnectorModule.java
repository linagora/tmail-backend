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

package com.linagora.tmail.migration.modules;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.core.Disconnector;
import org.apache.james.events.EventBus;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.disconnector.DisconnectionEventListener;
import com.linagora.tmail.disconnector.DisconnectorNotificationRegistration;
import com.linagora.tmail.disconnector.EventBusDisconnectorNotifier;
import com.linagora.tmail.migration.core.ProxyConnectionRegistry;

/**
 * Reuses the standard Twake Mail disconnection plumbing for the IMAP proxy so that migrating a user
 * disconnects its live sessions across a whole cluster of migration proxies:
 *
 * <ul>
 *     <li>the {@link ProxyConnectionRegistry} is exposed as the protocol-layer {@link Disconnector}
 *         that actually closes the tracked IMAP connections;</li>
 *     <li>the webadmin migration flow publishes a disconnection request through the
 *         {@link DisconnectorNotifier} onto the {@code TMAIL_EVENT_BUS};</li>
 *     <li>every node registers a {@link DisconnectionEventListener} on that event bus so the node
 *         holding the connection is the one that closes it, wherever the migration was triggered.</li>
 * </ul>
 *
 * <p>The event bus itself is contributed by a dedicated module chosen at assembly time (see
 * {@code MigrationProxyServer#chooseEventBusModule}), mirroring the other Twake Mail apps.
 */
public class MigrationProxyDisconnectorModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(Disconnector.class).to(ProxyConnectionRegistry.class);
    }

    @Provides
    @Singleton
    DisconnectorNotifier disconnectorNotifier(@Named("TMAIL_EVENT_BUS") EventBus tmailEventBus) {
        return new EventBusDisconnectorNotifier(tmailEventBus);
    }

    @ProvidesIntoSet
    InitializationOperation registerDisconnectionListener(DisconnectorNotificationRegistration registration) {
        return InitilizationOperationBuilder
            .forClass(DisconnectionListenerLoader.class)
            .init(registration::register);
    }

    /**
     * Marker {@link Startable} anchoring the {@link InitializationOperation} that registers the
     * disconnection listener on the event bus during server start-up.
     */
    public static class DisconnectionListenerLoader implements Startable {

    }
}
