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

import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.rabbitmq.client.BuiltinExchangeType;

public record QueueDeclaration(List<ExchangeBinding> bindings,
                               String queue,
                               String deadLetterExchange,
                               BuiltinExchangeType deadLetterExchangeType,
                               String deadLetterQueue,
                               Optional<String> deadLetterRoutingKey) {

    public static final BuiltinExchangeType DEFAULT_EXCHANGE_TYPE = BuiltinExchangeType.TOPIC;
    public static final BuiltinExchangeType DEFAULT_DEAD_LETTER_EXCHANGE_TYPE = BuiltinExchangeType.FANOUT;

    public record ExchangeBinding(String exchange, BuiltinExchangeType exchangeType, String routingKey) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ImmutableList.Builder<ExchangeBinding> bindings = ImmutableList.builder();
        private String queue;
        private String deadLetterQueue;
        private Optional<String> deadLetterExchange = Optional.empty();
        private BuiltinExchangeType deadLetterExchangeType = DEFAULT_DEAD_LETTER_EXCHANGE_TYPE;
        private Optional<String> deadLetterRoutingKey = Optional.empty();

        public Builder binding(String exchange, String routingKey) {
            return binding(exchange, DEFAULT_EXCHANGE_TYPE, routingKey);
        }

        public Builder binding(String exchange, BuiltinExchangeType exchangeType, String routingKey) {
            bindings.add(new ExchangeBinding(exchange, exchangeType, routingKey));
            return this;
        }

        public Builder queue(String queue) {
            this.queue = queue;
            return this;
        }

        /** Also names the dead letter exchange, unless {@link #deadLetterExchange} states otherwise. */
        public Builder deadLetterQueue(String deadLetterQueue) {
            this.deadLetterQueue = deadLetterQueue;
            return this;
        }

        public Builder deadLetterExchange(String deadLetterExchange, BuiltinExchangeType deadLetterExchangeType) {
            this.deadLetterExchange = Optional.of(deadLetterExchange);
            this.deadLetterExchangeType = deadLetterExchangeType;
            return this;
        }

        public Builder deadLetterRoutingKey(String deadLetterRoutingKey) {
            this.deadLetterRoutingKey = Optional.of(deadLetterRoutingKey);
            return this;
        }

        public QueueDeclaration build() {
            ImmutableList<ExchangeBinding> exchangeBindings = bindings.build();
            Preconditions.checkState(!exchangeBindings.isEmpty(), "'binding' is compulsory");
            Preconditions.checkState(queue != null, "'queue' is compulsory");
            Preconditions.checkState(deadLetterQueue != null, "'deadLetterQueue' is compulsory");

            return new QueueDeclaration(exchangeBindings, queue,
                deadLetterExchange.orElse(deadLetterQueue), deadLetterExchangeType,
                deadLetterQueue, deadLetterRoutingKey);
        }
    }
}
