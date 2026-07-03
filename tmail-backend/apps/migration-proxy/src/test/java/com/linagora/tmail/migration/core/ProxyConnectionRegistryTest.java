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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.netty.channel.embedded.EmbeddedChannel;

class ProxyConnectionRegistryTest {
    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Username ALICE = Username.of("alice@domain.tld");

    private ProxyConnectionRegistry testee;

    @BeforeEach
    void setUp() {
        testee = new ProxyConnectionRegistry();
    }

    @Test
    void disconnectShouldCloseRegisteredChannels() {
        EmbeddedChannel channel = new EmbeddedChannel();
        testee.register(BOB, channel);

        testee.disconnect(BOB::equals);

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void disconnectShouldCloseEveryChannelOfTheUser() {
        EmbeddedChannel first = new EmbeddedChannel();
        EmbeddedChannel second = new EmbeddedChannel();
        testee.register(BOB, first);
        testee.register(BOB, second);

        testee.disconnect(BOB::equals);

        assertThat(first.isOpen()).isFalse();
        assertThat(second.isOpen()).isFalse();
    }

    @Test
    void disconnectShouldCloseEveryMatchingUser() {
        EmbeddedChannel bobChannel = new EmbeddedChannel();
        EmbeddedChannel aliceChannel = new EmbeddedChannel();
        testee.register(BOB, bobChannel);
        testee.register(ALICE, aliceChannel);

        testee.disconnect(username -> true);

        assertThat(bobChannel.isOpen()).isFalse();
        assertThat(aliceChannel.isOpen()).isFalse();
    }

    @Test
    void disconnectShouldNotCloseChannelsOfNonMatchingUsers() {
        EmbeddedChannel bobChannel = new EmbeddedChannel();
        EmbeddedChannel aliceChannel = new EmbeddedChannel();
        testee.register(BOB, bobChannel);
        testee.register(ALICE, aliceChannel);

        testee.disconnect(BOB::equals);

        assertThat(aliceChannel.isOpen()).isTrue();
    }

    @Test
    void disconnectShouldNotThrowWhenUserHasNoConnection() {
        testee.disconnect(BOB::equals);
    }

    @Test
    void closedChannelShouldBeDeregistered() {
        EmbeddedChannel channel = new EmbeddedChannel();
        testee.register(BOB, channel);

        channel.close().syncUninterruptibly();

        // Once the channel closes it must be dropped from the registry rather than lingering as a dead entry.
        assertThat(testee.connectionCount(BOB)).isEqualTo(0);
    }
}
