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

package com.linagora.tmail.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.AmqpUri;

class SpaceConfigurationTest {
    @Test
    void fromShouldApplyDefaults() {
        assertThat(SpaceConfiguration.from(new BaseHierarchicalConfiguration()))
            .isEqualTo(SpaceConfiguration.DEFAULT);
    }

    @Test
    void fromShouldReadAllParameters() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("rabbitmq.uri", "amqp://user:pass@rabbitmq.example.com:5672/twake");
        configuration.addProperty("queue", "q");
        configuration.addProperty("deadLetterQueue", "dlq");
        configuration.addProperty("space.exchange", "spaces");
        configuration.addProperty("dns.exchange", "admin");
        configuration.addProperty("dns.routingKey", "dns.ok");
        configuration.addProperty("activity.exchange", "feed");
        configuration.addProperty("teamMailbox.prefix", "space-");
        configuration.addProperty("retention", "7d");

        assertThat(SpaceConfiguration.from(configuration))
            .isEqualTo(new SpaceConfiguration(Optional.of(AmqpUri.from("amqp://user:pass@rabbitmq.example.com:5672/twake")),
                "q", "dlq", "spaces", "admin", "dns.ok", "feed", "space-", Duration.ofDays(7)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Space-", "space", "spa ce-", "-"})
    void fromShouldRejectInvalidPrefixes(String prefix) {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("teamMailbox.prefix", prefix);

        assertThatThrownBy(() -> SpaceConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"queue", "space.exchange", "activity.exchange", "dns.routingKey"})
    void fromShouldRejectBlankValues(String parameter) {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(parameter, " ");

        assertThatThrownBy(() -> SpaceConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "Sales, '', sales",
        "Équipe Ventes, '', equipe-ventes",
        "  --Hello   World!!-- , '', hello-world",
        "R&D_team, '', r-d_team",
        "!!!, '', space",
        "Sales, space-, space-sales"
    })
    void mailboxNameShouldFollowTheSideServiceRules(String spaceName, String prefix, String expected) {
        assertThat(SpaceNames.mailboxName(spaceName, prefix)).isEqualTo(expected);
    }

    @Test
    void mailboxNameShouldBeAtMost64Characters() {
        assertThat(SpaceNames.mailboxName("a".repeat(100), "space-"))
            .hasSize(64)
            .startsWith("space-");
    }

    @Test
    void candidatesShouldAddSuffixesUpTo20() {
        assertThat(SpaceNames.candidates("sales"))
            .hasSize(20)
            .startsWith("sales", "sales-2", "sales-3")
            .endsWith("sales-20");
    }

    @Test
    void candidatesShouldStayWithin64Characters() {
        assertThat(SpaceNames.candidates("a".repeat(64)))
            .allMatch(candidate -> candidate.length() <= 64);
    }
}
