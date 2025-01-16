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

package com.linagora.tmail;

import java.io.Closeable;
import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.DisconnectorNotifier.InVMDisconnectorNotifier;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.Receiver;

public class RabbitMQDisconnectorConsumer implements Startable, Closeable {

    public static final String TMAIL_DISCONNECTOR_QUEUE_NAME = "tmail-disconnector-" +
        Throwing.supplier(() -> InetAddress.getLocalHost().getHostName()).get() +
        "-" + UUID.randomUUID();

    private static final boolean REQUEUE_ON_NACK = true;
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQDisconnectorConsumer.class);

    private final ReceiverProvider receiverProvider;
    private final InVMDisconnectorNotifier inVMDisconnectorNotifier;

    private Disposable consumeMessages;

    @Inject
    @Singleton
    public RabbitMQDisconnectorConsumer(ReceiverProvider receiverProvider,
                                        InVMDisconnectorNotifier inVMDisconnectorNotifier) {
        this.receiverProvider = receiverProvider;
        this.inVMDisconnectorNotifier = inVMDisconnectorNotifier;
    }

    public void start() {
        consumeMessages = doConsumeMessages();
    }

    public void restart() {
        Disposable previousConsumer = consumeMessages;
        consumeMessages = doConsumeMessages();
        Optional.ofNullable(previousConsumer).ifPresent(Disposable::dispose);
    }

    private Disposable doConsumeMessages() {
        return Flux.using(receiverProvider::createReceiver,
                receiver -> receiver.consumeManualAck(TMAIL_DISCONNECTOR_QUEUE_NAME),
                Receiver::close)
            .flatMap(this::consumeMessage)
            .subscribe();
    }

    private Mono<Void> consumeMessage(AcknowledgableDelivery ackDelivery) {
        return Mono.fromCallable(() -> DisconnectorRequestSerializer.deserialize(ackDelivery.getBody()))
            .flatMap(disconnectorRequest -> Mono.fromRunnable(() -> inVMDisconnectorNotifier.disconnect(disconnectorRequest)).then())
            .doOnSuccess(result -> ackDelivery.ack())
            .onErrorResume(error -> {
                LOGGER.error("Error when consume message", error);
                ackDelivery.nack(!REQUEUE_ON_NACK);
                return Mono.empty();
            });
    }

    @Override
    public void close() {
        Optional.ofNullable(consumeMessages).ifPresent(Disposable::dispose);
    }
}
