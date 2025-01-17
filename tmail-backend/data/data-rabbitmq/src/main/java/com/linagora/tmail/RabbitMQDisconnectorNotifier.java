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

import java.time.Duration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.DisconnectorNotifier;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;
import reactor.util.retry.Retry;

public class RabbitMQDisconnectorNotifier implements DisconnectorNotifier, Startable {
    public static final String TMAIL_DISCONNECTOR_EXCHANGE_NAME = "tmail-disconnector";
    public static final String ROUTING_KEY = StringUtils.EMPTY;

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQDisconnectorNotifier.class);
    private static final Retry RETRY_SPEC = Retry.backoff(2, Duration.ofMillis(100));

    private final Sender sender;
    private final DisconnectorRequestSerializer serializer;

    @Inject
    @Singleton
    public RabbitMQDisconnectorNotifier(Sender sender,
                                        DisconnectorRequestSerializer serializer) {
        this.sender = sender;
        this.serializer = serializer;
    }

    @Override
    public void disconnect(Request request) {
        try {
            sender.send(Mono.just(new OutboundMessage(TMAIL_DISCONNECTOR_EXCHANGE_NAME,
                    ROUTING_KEY,
                    serializer.serialize(request))))
                .retryWhen(RETRY_SPEC)
                .block();
        } catch (Exception exception) {
            LOGGER.error("Error while sending disconnection request", exception);
        }
    }
}
