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

package com.linagora.tmail.migration.core;

import java.io.Closeable;

import jakarta.annotation.PreDestroy;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.lifecycle.api.Startable;

import com.linagora.tmail.rabbitmq.ManagedRabbitMQConsumer;
import com.linagora.tmail.rabbitmq.QueueDeclaration;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;

/**
 * Consumes {@code migration:switch} messages ({@code {"migratedUser":{"mailAddress": "..."}}}) and marks
 * the carried user migrated, exactly as the webadmin {@code PUT /migratedUsers/{username}} route does
 * (see {@link MigrationSwitchService}). This lets an external migration orchestrator drive the old→new
 * switch over AMQP instead of webadmin.
 *
 * <p>Only wired up when {@code rabbitmq.properties} is present (see
 * {@code MigrationProxyServer.EventBusModuleChoice}), riding the same RabbitMQ backend as the disconnection
 * event bus.
 */
public class MigrationSwitchConsumer implements Startable, Closeable {
    public static final String EXCHANGE = "migration:switch";
    public static final String ROUTING_KEY = "mail";
    public static final String QUEUE = "migration:switch:proxy";
    private static final String DEAD_LETTER_QUEUE = QUEUE + "-dead-letter";

    private final ManagedRabbitMQConsumer consumer;

    public MigrationSwitchConsumer(ReactorRabbitMQChannelPool channelPool, MigrationSwitchService migrationSwitchService) {
        this.consumer = new ManagedRabbitMQConsumer.Factory(channelPool)
            .create(ManagedRabbitMQConsumer.Parameters.builder()
                .queueDeclaration(QueueDeclaration.builder()
                    .binding(EXCHANGE, ROUTING_KEY)
                    .queue(QUEUE)
                    .deadLetterQueue(DEAD_LETTER_QUEUE)
                    .build())
                .handleDelivery(delivery -> handle(delivery, migrationSwitchService))
                .build());
    }

    private Mono<Void> handle(AcknowledgableDelivery delivery, MigrationSwitchService migrationSwitchService) {
        return MigrationSwitchMessage.parseUsername(delivery.getBody())
            .map(migrationSwitchService::markMigrated)
            .orElseGet(Mono::empty);
    }

    public void init() {
        consumer.init();
    }

    @PreDestroy
    @Override
    public void close() {
        consumer.close();
    }
}
