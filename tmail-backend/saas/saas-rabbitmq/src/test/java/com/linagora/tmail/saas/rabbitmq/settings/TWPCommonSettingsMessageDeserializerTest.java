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

package com.linagora.tmail.saas.rabbitmq.settings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

public class TWPCommonSettingsMessageDeserializerTest {
    @Test
    void parseInvalidAmqpMessageShouldThrowException() {
        String twpSettingsMessage = "{ invalid json }";

        assertThatThrownBy(() -> TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(twpSettingsMessage))
            .isInstanceOf(TWPCommonSettingsMessage.TWPSettingsMessageParseException.class)
            .hasMessageContaining("Failed to parse TWP settings message");
    }

    @Test
    void parseValidAmqpMessageShouldSucceed() {
        String amqpMessage = """
            {
                "source": "twake-mail",
                "nickname": "alice",
                "request_id": "6de4a2d1-b322-42cd-9e49-fed5d3f9c9b7",
                "timestamp": 176248374356283740,
                "version": 1,
                "payload": {
                    "email": "alice@domain.tld",
                    "language": "fr"
                }
            }
            """;

        TWPCommonSettingsMessage twpCommonSettingsMessage = TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(amqpMessage);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(twpCommonSettingsMessage.source()).isEqualTo("twake-mail");
            softly.assertThat(twpCommonSettingsMessage.nickname()).isEqualTo("alice");
            softly.assertThat(twpCommonSettingsMessage.requestId()).isEqualTo("6de4a2d1-b322-42cd-9e49-fed5d3f9c9b7");
            softly.assertThat(twpCommonSettingsMessage.timestamp().longValue()).isEqualTo(176248374356283740L);
            softly.assertThat(twpCommonSettingsMessage.payload().language()).isEqualTo(Optional.of("fr"));
        });
    }

    @Test
    void parseMissingRequiredSourceShouldThrowException() {
        String amqpMessage = """
            {
                "nickname": "alice",
                "request_id": "6de4a2d1-b322-42cd-9e49-fed5d3f9c9b7",
                "timestamp": 176248374356283740,
                "version": 1,
                "payload": {
                    "email": "alice@domain.tld",
                    "language": "fr"
                }
            }
            """;

        assertThatThrownBy(() -> TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(amqpMessage))
            .isInstanceOf(TWPCommonSettingsMessage.TWPSettingsMessageParseException.class)
            .hasMessageContaining("Failed to parse TWP settings message");
    }

    @Test
    void parseMissingRequiredNicknameShouldThrowException() {
        String amqpMessage = """
            {
                "source": "twake-mail",
                "request_id": "6de4a2d1-b322-42cd-9e49-fed5d3f9c9b7",
                "timestamp": 176248374356283740,
                "version": 1,
                "payload": {
                    "email": "alice@domain.tld",
                    "language": "fr"
                }
            }
            """;

        assertThatThrownBy(() -> TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(amqpMessage))
            .isInstanceOf(TWPCommonSettingsMessage.TWPSettingsMessageParseException.class)
            .hasMessageContaining("Failed to parse TWP settings message");
    }

    @Test
    void parseMissingRequiredRequestIdShouldThrowException() {
        String amqpMessage = """
            {
                "source": "twake-mail",
                "nickname": "alice",
                "timestamp": 176248374356283740,
                "version": 1,
                "payload": {
                    "email": "alice@domain.tld",
                    "language": "fr"
                }
            }
            """;

        assertThatThrownBy(() -> TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(amqpMessage))
            .isInstanceOf(TWPCommonSettingsMessage.TWPSettingsMessageParseException.class)
            .hasMessageContaining("Failed to parse TWP settings message");
    }

    @Test
    void parseMissingRequiredTimestampShouldThrowException() {
        String amqpMessage = """
            {
                "source": "twake-mail",
                "nickname": "alice",
                "version": 1,
                "request_id": "6de4a2d1-b322-42cd-9e49-fed5d3f9c9b7",
                "payload": {
                    "email": "alice@domain.tld",
                    "language": "fr"
                }
            }
            """;

        assertThatThrownBy(() -> TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(amqpMessage))
            .isInstanceOf(TWPCommonSettingsMessage.TWPSettingsMessageParseException.class)
            .hasMessageContaining("Failed to parse TWP settings message");
    }

    @Test
    void parseMissingRequiredPayloadShouldThrowException() {
        String amqpMessage = """
            {
                "source": "twake-mail",
                "nickname": "alice",
                "version": 1,
                "request_id": "6de4a2d1-b322-42cd-9e49-fed5d3f9c9b7",
                "timestamp": 176248374356283740
            }
            """;

        assertThatThrownBy(() -> TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(amqpMessage))
            .isInstanceOf(TWPCommonSettingsMessage.TWPSettingsMessageParseException.class)
            .hasMessageContaining("Failed to parse TWP settings message");
    }

    @Test
    void parseMissingRequiredEmailShouldThrowException() {
        String amqpMessage = """
            {
                "source": "twake-mail",
                "nickname": "alice",
                "request_id": "6de4a2d1-b322-42cd-9e49-fed5d3f9c9b7",
                "timestamp": 176248374356283740,
                "version": 1,
                "payload": {
                    "language": "fr"
                }
            }
            """;

        assertThatThrownBy(() -> TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(amqpMessage))
            .isInstanceOf(TWPCommonSettingsMessage.TWPSettingsMessageParseException.class)
            .hasMessageContaining("Failed to parse TWP settings message");
    }

    @Test
    void parseMissingRequiredVersionShouldThrowException() {
        String amqpMessage = """
            {
                "nickname": "alice",
                "request_id": "6de4a2d1-b322-42cd-9e49-fed5d3f9c9b7",
                "timestamp": 176248374356283740,
                "version": 1,
                "payload": {
                    "email": "alice@domain.tld",
                    "language": "fr"
                }
            }
            """;

        assertThatThrownBy(() -> TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(amqpMessage))
            .isInstanceOf(TWPCommonSettingsMessage.TWPSettingsMessageParseException.class)
            .hasMessageContaining("Failed to parse TWP settings message");
    }

    @Test
    void parsePayloadWithExtraFieldsShouldNotFail() {
        String amqpMessage = """
            {
                "source": "twake-mail",
                "nickname": "alice",
                "request_id": "6de4a2d1-b322-42cd-9e49-fed5d3f9c9b7",
                "timestamp": 176248374356283740,
                "version": 1,
                "payload": {
                    "email": "alice@domain.tld",
                    "language": "fr",
                    "who_care": "whatever"
                },
                "who_care": "whatever"
            }
            """;

        TWPCommonSettingsMessage twpCommonSettingsMessage = TWPCommonSettingsMessage.Deserializer.parseAMQPMessage(amqpMessage);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(twpCommonSettingsMessage.source()).isEqualTo("twake-mail");
            softly.assertThat(twpCommonSettingsMessage.nickname()).isEqualTo("alice");
            softly.assertThat(twpCommonSettingsMessage.requestId()).isEqualTo("6de4a2d1-b322-42cd-9e49-fed5d3f9c9b7");
            softly.assertThat(twpCommonSettingsMessage.timestamp().longValue()).isEqualTo(176248374356283740L);
            softly.assertThat(twpCommonSettingsMessage.payload().language()).isEqualTo(Optional.of("fr"));
        });
    }
}
