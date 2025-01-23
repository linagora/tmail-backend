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

package com.linagora.tmail.contact;

import java.time.Duration;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.linagora.tmail.james.jmap.contact.ContactAddIndexingProcessor;
import com.linagora.tmail.james.jmap.contact.ContactFields;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;
import reactor.util.retry.Retry;

public class OpenPaasAddContactProcessor implements ContactAddIndexingProcessor {
    private static final Retry RETRY_SPEC = Retry.backoff(2, Duration.ofMillis(100));

    private final Sender sender;
    private final String addContactRoutingKey;
    private final String addContactExchangeName;

    public OpenPaasAddContactProcessor(ReactorRabbitMQChannelPool reactorRabbitMQChannelPool,
                                       String addContactRoutingKey, String addContactExchangeName) {
        this.addContactRoutingKey = addContactRoutingKey;
        this.addContactExchangeName = addContactExchangeName;
        reactorRabbitMQChannelPool.start();
        this.sender = reactorRabbitMQChannelPool.getSender();
    }

    @Override
    public Publisher<Void> process(Username username, ContactFields contactFields) {
        return sender.send(Mono.just(new OutboundMessage(addContactExchangeName,
                addContactRoutingKey,
                serializeContactFields(username, contactFields))))
            .retryWhen(RETRY_SPEC);
    }

    private byte[] serializeContactFields(Username username, ContactFields contactFields) {
        // TODO
        return null;
    }
}
