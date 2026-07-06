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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import jakarta.inject.Singleton;

import org.apache.james.core.Disconnector;
import org.apache.james.core.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.netty.channel.Channel;

/**
 * Tracks the live proxied client connections per user and closes them on demand as the protocol-layer
 * {@link Disconnector} of the IMAP proxy. When a user is flipped to "migrated" their in-flight IMAP
 * sessions are still pinned to the old backend (the backend was resolved at LOGIN time); closing them
 * forces the clients to reconnect and be routed to the new backend straight away instead of lingering
 * on the old one until they happen to disconnect on their own.
 *
 * <p>The migration flow does not call this registry directly: it dispatches a disconnection request on
 * the event bus (see {@code EventBusDisconnectorNotifier}) so that, in a cluster of migration proxies,
 * the node actually holding the user's connection is the one that closes it.
 */
@Singleton
public class ProxyConnectionRegistry implements Disconnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConnectionRegistry.class);

    private final ConcurrentMap<Username, Set<Channel>> channelsByUser = new ConcurrentHashMap<>();

    public void register(Username username, Channel clientChannel) {
        // compute() keeps the get-or-create and the add atomic per user so a concurrent closeConnections()
        // cannot remove the entry in between and leave clientChannel tracked in an orphaned Set.
        channelsByUser.compute(username, (key, channels) -> {
            Set<Channel> tracked = channels != null ? channels : ConcurrentHashMap.newKeySet();
            tracked.add(clientChannel);
            return tracked;
        });
        // Drop the channel from the registry once it closes so we do not accumulate dead connections.
        clientChannel.closeFuture().addListener(future -> deregister(username, clientChannel));
    }

    @Override
    public void disconnect(Predicate<Username> matcher) {
        channelsByUser.keySet().stream()
            .filter(matcher)
            .forEach(this::closeConnections);
    }

    private void closeConnections(Username username) {
        // Remove and close under the same atomic compute() as register() so we never miss a channel that
        // is being registered concurrently (it would otherwise stay open and pinned to the old backend).
        channelsByUser.compute(username, (key, channels) -> {
            if (channels != null && !channels.isEmpty()) {
                LOGGER.info("Closing {} proxied connection(s) of {} to force reconnection after migration",
                    channels.size(), username.asString());
                channels.forEach(Channel::close);
            }
            return null;
        });
    }

    private void deregister(Username username, Channel clientChannel) {
        channelsByUser.computeIfPresent(username, (key, channels) -> {
            channels.remove(clientChannel);
            return channels.isEmpty() ? null : channels;
        });
    }

    @VisibleForTesting
    int connectionCount(Username username) {
        return channelsByUser.getOrDefault(username, Set.of()).size();
    }
}
