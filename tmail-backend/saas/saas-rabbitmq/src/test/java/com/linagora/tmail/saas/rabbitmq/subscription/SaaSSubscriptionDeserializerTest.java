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

import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

class SaaSSubscriptionDeserializerTest {
    RateLimitingDefinition RATE_LIMITING_1 = RateLimitingDefinition.builder()
        .mailsSentPerMinute(10L)
        .mailsSentPerHours(100L)
        .mailsSentPerDays(1000L)
        .mailsReceivedPerMinute(20L)
        .mailsReceivedPerHours(200L)
        .mailsReceivedPerDays(2000L)
        .build();

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
                "internalEmail": "alice@twake.app",
                "isPaying": true,
                "canUpgrade": true,
                "features": {
                    "mail": {
                        "storageQuota": 12334534,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
            }
            """;

        SaaSSubscriptionMessage message = SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(validMessage);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(message.internalEmail()).isEqualTo("alice@twake.app");
            softly.assertThat(message.isPaying()).isTrue();
            softly.assertThat(message.canUpgrade()).isTrue();
            softly.assertThat(message.features().mail().storageQuota()).isEqualTo(12334534L);
            softly.assertThat(message.features().mail().rateLimitingDefinition()).isEqualTo(RATE_LIMITING_1);
        });
    }

    @Test
    void parseMissingRequiredInternalEmailShouldThrowException() {
        String message = """
            {
                "isPaying": true,
                "canUpgrade": true,
                "features": {
                    "mail": {
                        "storageQuota": 12334534,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
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
                "internalEmail": "alice@twake.app",
                "canUpgrade": true,
                "features": {
                    "mail": {
                        "storageQuota": 12334534,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
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
                "internalEmail": "alice@twake.app",
                "isPaying": true,
                "features": {
                    "mail": {
                        "storageQuota": 12334534,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
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
                "internalEmail": "alice@twake.app",
                "isPaying": true,
                "canUpgrade": true
            }
            """;

        assertThatThrownBy(() -> SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(message))
            .isInstanceOf(SaaSSubscriptionMessage.SaaSSubscriptionMessageParseException.class)
            .hasMessageContaining("Failed to parse SaaS subscription message");
    }

    @Test
    void parseMissingRequiredRateLimitingShouldThrowException() {
        String message = """
            {
                "username": "alice@twake.app",
                "isPaying": true,
                "canUpgrade": true,
                "features": {
                    "mail": {
                        "storageQuota": 12334534
                    }
                }
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
                "internalEmail": "alice@twake.app",
                "isPaying": true,
                "canUpgrade": true,
                "features": {
                    "mail": {
                        "storageQuota": 123,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                },
                "extraField": "ignored"
            }
            """;

        SaaSSubscriptionMessage parsed = SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(message);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(parsed.internalEmail()).isEqualTo("alice@twake.app");
            softly.assertThat(parsed.isPaying()).isTrue();
            softly.assertThat(parsed.canUpgrade()).isTrue();
            softly.assertThat(parsed.features().mail().storageQuota()).isEqualTo(123L);
            softly.assertThat(parsed.features().mail().rateLimitingDefinition()).isEqualTo(RATE_LIMITING_1);
        });
    }

    @Test
    void parseNegativeStorageQuotaShouldSucceed() {
        String message = """
            {
                "internalEmail": "alice@twake.app",
                "isPaying": true,
                "canUpgrade": true,
                "features": {
                    "mail": {
                        "storageQuota": -1,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
            }
            """;

        SaaSSubscriptionMessage parsed = SaaSSubscriptionMessage.Deserializer.parseAMQPMessage(message);

        assertThat(parsed.features().mail().storageQuota()).isEqualTo(-1L);
    }
}
