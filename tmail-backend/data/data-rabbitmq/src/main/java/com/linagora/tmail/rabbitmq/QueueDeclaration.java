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

import com.google.common.collect.ImmutableList;
import com.rabbitmq.client.BuiltinExchangeType;

public record QueueDeclaration(List<ExchangeBinding> bindings,
                               String queue,
                               String deadLetterExchange,
                               BuiltinExchangeType deadLetterExchangeType,
                               String deadLetterQueue,
                               Optional<String> deadLetterRoutingKey) {

    public record ExchangeBinding(String exchange, BuiltinExchangeType exchangeType, String routingKey) {
    }

    public static QueueDeclaration singleExchange(String exchange, BuiltinExchangeType exchangeType, String routingKey,
                                                  String queue,
                                                  String deadLetterExchange, BuiltinExchangeType deadLetterExchangeType, String deadLetterQueue,
                                                  Optional<String> deadLetterRoutingKey) {
        return new QueueDeclaration(ImmutableList.of(new ExchangeBinding(exchange, exchangeType, routingKey)),
            queue, deadLetterExchange, deadLetterExchangeType, deadLetterQueue, deadLetterRoutingKey);
    }
}
