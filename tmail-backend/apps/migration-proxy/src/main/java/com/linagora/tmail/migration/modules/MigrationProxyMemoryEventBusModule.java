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

package com.linagora.tmail.migration.modules;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.events.EventBus;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.metrics.api.MetricFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

/**
 * In-VM implementation of the {@code TMAIL_EVENT_BUS} that carries the disconnection requests. This is
 * the single-node option of the event bus module chooser: disconnections triggered on a node only reach
 * that same node, which is enough when the migration proxy is deployed as a single instance. A clustered
 * deployment plugs a distributed (RabbitMQ) event bus here instead so the request reaches every node.
 */
public class MigrationProxyMemoryEventBusModule extends AbstractModule {
    @Provides
    @Singleton
    @Named("TMAIL_EVENT_BUS")
    EventBus provideTmailEventBus(MetricFactory metricFactory) {
        return new InVMEventBus(new InVmEventDelivery(metricFactory),
            RetryBackoffConfiguration.DEFAULT,
            new MemoryEventDeadLetters());
    }
}
