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
 *******************************************************************/

package com.linagora.tmail.saas.rabbitmq.subscription;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

public class SaaSDomainSubscriptionDeserializerTest {
    @Test
    void parseInvalidAmqpMessageShouldThrowException() {
        String invalidMessage = "{ invalid json }";

        assertThatThrownBy(() -> SaaSSubscriptionDeserializer.parseAMQPDomainMessage(invalidMessage))
            .isInstanceOf(SaaSSubscriptionDeserializer.SaaSSubscriptionMessageParseException.class)
            .hasMessageContaining("Failed to parse SaaS subscription domain message");
    }

    @Test
    void parseValidAmqpMessageShouldSucceed() {
        String validMessage = """
            {
                "domain": "twake.app",
                "validated": true
            }
            """;

        SaaSDomainSubscriptionMessage message = SaaSSubscriptionDeserializer.parseAMQPDomainMessage(validMessage);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(message.domain()).isEqualTo("twake.app");
            softly.assertThat(message.validated()).isTrue();
        });
    }

    @Test
    void parseMissingRequiredDomainShouldThrowException() {
        String message = """
            {
                "validated": true
            }
            """;

        assertThatThrownBy(() -> SaaSSubscriptionDeserializer.parseAMQPDomainMessage(message))
            .isInstanceOf(SaaSSubscriptionDeserializer.SaaSSubscriptionMessageParseException.class)
            .hasMessageContaining("Failed to parse SaaS subscription domain message");
    }

    @Test
    void parseMissingRequiredValidatedShouldThrowException() {
        String message = """
            {
                "domain": "twake.app"
            }
            """;

        assertThatThrownBy(() -> SaaSSubscriptionDeserializer.parseAMQPDomainMessage(message))
            .isInstanceOf(SaaSSubscriptionDeserializer.SaaSSubscriptionMessageParseException.class)
            .hasMessageContaining("Failed to parse SaaS subscription domain message");
    }

    @Test
    void parseMessageWithExtraFieldsShouldNotFail() {
        String validMessage = """
            {
                "domain": "twake.app",
                "validated": true,
                "extraField": "ignored"
            }
            """;

        SaaSDomainSubscriptionMessage message = SaaSSubscriptionDeserializer.parseAMQPDomainMessage(validMessage);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(message.domain()).isEqualTo("twake.app");
            softly.assertThat(message.validated()).isTrue();
        });
    }
}
