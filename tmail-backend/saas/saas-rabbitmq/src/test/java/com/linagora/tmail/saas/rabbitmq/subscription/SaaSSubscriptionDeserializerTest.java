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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class SaaSSubscriptionDeserializerTest {
    @Test
    void parseInvalidAmqpMessageShouldThrowException() {
        String invalidMessage = "{ invalid json }";
        
        assertThatThrownBy(() -> SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(invalidMessage))
            .isInstanceOf(SaaSSubscriptionMessage.SaaSSubscriptionMessageParseException.class)
            .hasMessageContaining("Failed to parse SaaS subscription message");
    }

    @Test
    void parseValidAmqpMessageShouldSucceed() {
        String validMessage = """
            {
                "username": "alice@twake.app",
                "isPaying": true,
                "canUpgrade": true,
                "mail": { "storageQuota": 12334534 }
            }
            """;

        SaaSSubscriptionMessage message = SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(validMessage);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(message.username()).isEqualTo("alice@twake.app");
            softly.assertThat(message.isPaying()).isTrue();
            softly.assertThat(message.canUpgrade()).isTrue();
            softly.assertThat(message.mail().storageQuota()).isEqualTo(12334534L);
        });
    }

    @Test
    void parseMissingRequiredUsernameShouldThrowException() {
        String message = """
            {
                "isPaying": true,
                "canUpgrade": true,
                "mail": { "storageQuota": 12334534 }
            }
            """;

        assertThatThrownBy(() -> SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(message))
            .isInstanceOf(SaaSSubscriptionMessage.SaaSSubscriptionMessageParseException.class)
            .hasMessageContaining("Failed to parse SaaS subscription message");
    }

    @Test
    void parseMissingRequiredIsPayingShouldThrowException() {
        String message = """
            {
                "username": "alice@twake.app",
                "canUpgrade": true,
                "mail": { "storageQuota": 12334534 }
            }
            """;

        assertThatThrownBy(() -> SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(message))
            .isInstanceOf(SaaSSubscriptionMessage.SaaSSubscriptionMessageParseException.class)
            .hasMessageContaining("Failed to parse SaaS subscription message");
    }

    @Test
    void parseMissingRequiredCanUpgradeShouldThrowException() {
        String message = """
            {
                "username": "alice@twake.app",
                "isPaying": true,
                "mail": { "storageQuota": 12334534 }
            }
            """;

        assertThatThrownBy(() -> SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(message))
            .isInstanceOf(SaaSSubscriptionMessage.SaaSSubscriptionMessageParseException.class)
            .hasMessageContaining("Failed to parse SaaS subscription message");
    }

    @Test
    void parseMissingRequiredMailPayloadShouldThrowException() {
        String message = """
            {
                "username": "alice@twake.app",
                "isPaying": true,
                "canUpgrade": true,
                "planName": "twake_standard"
            }
            """;

        assertThatThrownBy(() -> SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(message))
            .isInstanceOf(SaaSSubscriptionMessage.SaaSSubscriptionMessageParseException.class)
            .hasMessageContaining("Failed to parse SaaS subscription message");
    }

    @Test
    void parseMessageWithExtraFieldsShouldNotFail() {
        String message = """
            {
                "username": "alice@twake.app",
                "isPaying": true,
                "canUpgrade": true,
                "mail": { "storageQuota": 123, "extraField": "ignored" },
                "extraField": "ignored"
            }
            """;

        SaaSSubscriptionMessage parsed = SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(message);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(parsed.username()).isEqualTo("alice@twake.app");
            softly.assertThat(parsed.isPaying()).isTrue();
            softly.assertThat(parsed.canUpgrade()).isTrue();
            softly.assertThat(parsed.mail().storageQuota()).isEqualTo(123L);
        });
    }

    @Test
    void parseNegativeStorageQuotaShouldSucceed() {
        String message = """
            {
                "username": "alice@twake.app",
                "isPaying": true,
                "canUpgrade": true,
                "mail": { "storageQuota": -1 }
            }
            """;

        SaaSSubscriptionMessage parsed = SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(message);

        assertThat(parsed.mail().storageQuota()).isEqualTo(-1L);
    }
}
