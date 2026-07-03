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
    void closeConnectionsShouldCloseRegisteredChannels() {
        EmbeddedChannel channel = new EmbeddedChannel();
        testee.register(BOB, channel);

        testee.closeConnections(BOB);

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void closeConnectionsShouldCloseEveryChannelOfTheUser() {
        EmbeddedChannel first = new EmbeddedChannel();
        EmbeddedChannel second = new EmbeddedChannel();
        testee.register(BOB, first);
        testee.register(BOB, second);

        testee.closeConnections(BOB);

        assertThat(first.isOpen()).isFalse();
        assertThat(second.isOpen()).isFalse();
    }

    @Test
    void closeConnectionsShouldNotCloseChannelsOfOtherUsers() {
        EmbeddedChannel bobChannel = new EmbeddedChannel();
        EmbeddedChannel aliceChannel = new EmbeddedChannel();
        testee.register(BOB, bobChannel);
        testee.register(ALICE, aliceChannel);

        testee.closeConnections(BOB);

        assertThat(aliceChannel.isOpen()).isTrue();
    }

    @Test
    void closeConnectionsShouldNotThrowWhenUserHasNoConnection() {
        testee.closeConnections(BOB);
    }

    @Test
    void closedChannelShouldBeDeregistered() {
        EmbeddedChannel channel = new EmbeddedChannel();
        testee.register(BOB, channel);

        channel.close().syncUninterruptibly();

        // A second close attempt through the registry must be a no-op: the channel is already gone.
        testee.closeConnections(BOB);
        assertThat(channel.isOpen()).isFalse();
    }
}
