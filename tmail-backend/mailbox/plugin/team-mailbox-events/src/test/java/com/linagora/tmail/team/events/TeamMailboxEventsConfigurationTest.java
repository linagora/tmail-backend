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

package com.linagora.tmail.team.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.AmqpUri;

class TeamMailboxEventsConfigurationTest {
    private static final String AMQP_URI = "amqp://user:pass@rabbitmq.example.com:5673/vhost";

    private BaseHierarchicalConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("amqpUri", AMQP_URI);
        configuration.addProperty("exchange", "team-events");
        configuration.addProperty("receivedRoutingKey", "team.received");
        configuration.addProperty("sentRoutingKey", "team.sent");
    }

    @Test
    void fromShouldReadAllParameters() {
        assertThat(TeamMailboxEventsConfiguration.from(configuration))
            .isEqualTo(new TeamMailboxEventsConfiguration(Optional.of(AmqpUri.from(AMQP_URI)), "team-events",
                "team.received", "team.sent"));
    }

    @Test
    void fromShouldApplyDefaultsWhenParametersAreMissing() {
        assertThat(TeamMailboxEventsConfiguration.from(new BaseHierarchicalConfiguration()))
            .isEqualTo(new TeamMailboxEventsConfiguration(Optional.empty(), "tmail",
                "team-mailbox.message.received", "team-mailbox.message.sent"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"amqpUri", "exchange", "receivedRoutingKey", "sentRoutingKey"})
    void fromShouldFailWhenAParameterIsBlank(String parameter) {
        configuration.setProperty(parameter, "  ");

        assertThatThrownBy(() -> TeamMailboxEventsConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
