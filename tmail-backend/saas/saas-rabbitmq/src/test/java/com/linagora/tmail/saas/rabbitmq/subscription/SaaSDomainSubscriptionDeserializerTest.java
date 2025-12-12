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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage;
import com.linagora.tmail.saas.rabbitmq.subscription.SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage;

public class SaaSDomainSubscriptionDeserializerTest {
    RateLimitingDefinition RATE_LIMITING_1 = RateLimitingDefinition.builder()
        .mailsSentPerMinute(10L)
        .mailsSentPerHours(100L)
        .mailsSentPerDays(1000L)
        .mailsReceivedPerMinute(20L)
        .mailsReceivedPerHours(200L)
        .mailsReceivedPerDays(2000L)
        .build();

    @Nested
    class SaaSDomainValidSubscriptionDeserializerTest {
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
                "validated": true,
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

            SaaSDomainValidSubscriptionMessage message = (SaaSDomainValidSubscriptionMessage) SaaSSubscriptionDeserializer.parseAMQPDomainMessage(validMessage);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(message.domain()).isEqualTo("twake.app");
                softly.assertThat(message.validated()).contains(true);
                softly.assertThat(message.features().mail().get().storageQuota()).isEqualTo(12334534L);
                softly.assertThat(message.features().mail().get().rateLimitingDefinition()).isEqualTo(RATE_LIMITING_1);
            });
        }

        @Test
        void parseNullAmqpMessageShouldSucceed() {
            String validMessage = """
            {
                "domain": "twake.app",
                "validated": null,
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

            SaaSDomainValidSubscriptionMessage message = (SaaSDomainValidSubscriptionMessage) SaaSSubscriptionDeserializer.parseAMQPDomainMessage(validMessage);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(message.domain()).isEqualTo("twake.app");
                softly.assertThat(message.validated()).contains(true);
                softly.assertThat(message.features().mail().get().storageQuota()).isEqualTo(12334534L);
                softly.assertThat(message.features().mail().get().rateLimitingDefinition()).isEqualTo(RATE_LIMITING_1);
            });
        }

        @Test
        void parseUnspecifiedValidAmqpMessageShouldSucceed() {
            String validMessage = """
            {
                "domain": "twake.app",
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

            SaaSDomainValidSubscriptionMessage message = (SaaSDomainValidSubscriptionMessage) SaaSSubscriptionDeserializer.parseAMQPDomainMessage(validMessage);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(message.domain()).isEqualTo("twake.app");
                softly.assertThat(message.validated()).isEmpty();
                softly.assertThat(message.features().mail().get().storageQuota()).isEqualTo(12334534L);
                softly.assertThat(message.features().mail().get().rateLimitingDefinition()).isEqualTo(RATE_LIMITING_1);
            });
        }

        @Test
        void parseMissingRequiredDomainShouldThrowException() {
            String message = """
            {
                "validated": true,
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

            assertThatThrownBy(() -> SaaSSubscriptionDeserializer.parseAMQPDomainMessage(message))
                .isInstanceOf(SaaSSubscriptionDeserializer.SaaSSubscriptionMessageParseException.class)
                .hasMessageContaining("Failed to parse SaaS subscription domain message");
        }

        @Test
        void parseMissingRequiredValidatedShouldThrowException() {
            String message = """
            {
                "domain": "twake.app",
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

            assertThatThrownBy(() -> SaaSSubscriptionDeserializer.parseAMQPDomainMessage(message))
                .isInstanceOf(SaaSSubscriptionDeserializer.SaaSSubscriptionMessageParseException.class)
                .hasMessageContaining("Failed to parse SaaS subscription domain message");
        }

        @Test
        void parseMissingRequiredMailPayloadShouldThrowException() {
            String message = """
            {
                "domain": "twake.app",
                "validated": true
            }
            """;

            assertThatThrownBy(() -> SaaSSubscriptionDeserializer.parseAMQPDomainMessage(message))
                .isInstanceOf(SaaSSubscriptionDeserializer.SaaSSubscriptionMessageParseException.class)
                .hasMessageContaining("Failed to parse SaaS subscription domain message");
        }

        @Test
        void parseMissingRequiredRateLimitingShouldThrowException() {
            String message = """
            {
                "domain": "twake.app",
                "validated": true,
                "features": {
                    "mail": {
                        "storageQuota": 12334534
                    }
                }
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
                },
                "extraField": "ignored"
            }
            """;

            SaaSDomainValidSubscriptionMessage message = (SaaSDomainValidSubscriptionMessage) SaaSSubscriptionDeserializer.parseAMQPDomainMessage(validMessage);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(message.domain()).isEqualTo("twake.app");
                softly.assertThat(message.validated()).contains(true);
            });
        }

        @Test
        void parseNegativeStorageQuotaShouldSucceed() {
            String message = """
            {
                "domain": "twake.app",
                "validated": true,
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

            SaaSDomainValidSubscriptionMessage parsed = (SaaSDomainValidSubscriptionMessage) SaaSSubscriptionDeserializer.parseAMQPDomainMessage(message);

            assertThat(parsed.features().mail().get().storageQuota()).isEqualTo(-1L);
        }
    }

    @Nested
    class SaaSDomainCancelSubscriptionDeserializerTest {
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
                "enabled": false
            }
            """;

            SaaSDomainCancelSubscriptionMessage message = (SaaSDomainCancelSubscriptionMessage) SaaSSubscriptionDeserializer.parseAMQPDomainMessage(validMessage);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(message.domain()).isEqualTo("twake.app");
                softly.assertThat(message.enabled()).isFalse();
            });
        }

        @Test
        void parseMissingRequiredDomainShouldThrowException() {
            String message = """
            {
                "enabled": false
            }
            """;

            assertThatThrownBy(() -> SaaSSubscriptionDeserializer.parseAMQPDomainMessage(message))
                .isInstanceOf(SaaSSubscriptionDeserializer.SaaSSubscriptionMessageParseException.class)
                .hasMessageContaining("Failed to parse SaaS subscription domain message");
        }

        @Test
        void parseMissingRequiredEnabledShouldThrowException() {
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
                "enabled": true,
                "extraField": "ignored"
            }
            """;

            SaaSDomainCancelSubscriptionMessage message = (SaaSDomainCancelSubscriptionMessage) SaaSSubscriptionDeserializer.parseAMQPDomainMessage(validMessage);

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(message.domain()).isEqualTo("twake.app");
                softly.assertThat(message.enabled()).isTrue();
            });
        }
    }
}
