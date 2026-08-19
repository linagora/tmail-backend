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

package com.linagora.tmail.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.BuiltinExchangeType;

class QueueDeclarationTest {

    private static QueueDeclaration.Builder minimalBuilder() {
        return QueueDeclaration.builder()
            .binding("exchange", "routing-key")
            .queue("queue")
            .deadLetterQueue("dead-letter-queue");
    }

    @Test
    void bindingShouldDefaultToTopicExchange() {
        assertThat(minimalBuilder().build().bindings())
            .containsExactly(new QueueDeclaration.ExchangeBinding("exchange", BuiltinExchangeType.TOPIC, "routing-key"));
    }

    @Test
    void deadLetterExchangeShouldDefaultToTheDeadLetterQueueName() {
        assertThat(minimalBuilder().build().deadLetterExchange())
            .isEqualTo("dead-letter-queue");
    }

    @Test
    void deadLetterExchangeShouldDefaultToFanout() {
        assertThat(minimalBuilder().build().deadLetterExchangeType())
            .isEqualTo(BuiltinExchangeType.FANOUT);
    }

    @Test
    void deadLetterRoutingKeyShouldDefaultToEmpty() {
        assertThat(minimalBuilder().build().deadLetterRoutingKey())
            .isEmpty();
    }

    @Test
    void deadLetterExchangeShouldOverrideDefaults() {
        QueueDeclaration queueDeclaration = minimalBuilder()
            .deadLetterExchange("dead-letter-exchange", BuiltinExchangeType.DIRECT)
            .deadLetterRoutingKey("")
            .build();

        assertThat(queueDeclaration.deadLetterExchange()).isEqualTo("dead-letter-exchange");
        assertThat(queueDeclaration.deadLetterExchangeType()).isEqualTo(BuiltinExchangeType.DIRECT);
        assertThat(queueDeclaration.deadLetterRoutingKey()).isEqualTo(Optional.of(""));
    }

    @Test
    void buildShouldPreserveBindingOrder() {
        assertThat(QueueDeclaration.builder()
                .binding("first", "first-routing-key")
                .binding("second", "second-routing-key")
                .queue("queue")
                .deadLetterQueue("dead-letter-queue")
                .build()
                .bindings())
            .extracting(QueueDeclaration.ExchangeBinding::exchange)
            .containsExactly("first", "second");
    }

    @Test
    void buildShouldThrowWhenNoBinding() {
        assertThatThrownBy(() -> QueueDeclaration.builder()
                .queue("queue")
                .deadLetterQueue("dead-letter-queue")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenNoQueue() {
        assertThatThrownBy(() -> QueueDeclaration.builder()
                .binding("exchange", "routing-key")
                .deadLetterQueue("dead-letter-queue")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowWhenNoDeadLetterQueue() {
        assertThatThrownBy(() -> QueueDeclaration.builder()
                .binding("exchange", "routing-key")
                .queue("queue")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }
}
