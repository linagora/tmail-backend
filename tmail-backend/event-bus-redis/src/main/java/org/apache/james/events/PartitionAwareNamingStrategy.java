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

package org.apache.james.events;

import com.google.common.base.Preconditions;

import reactor.rabbitmq.QueueSpecification;

public class PartitionAwareNamingStrategy implements NamingStrategy {
    private final EventBusName baseEventBusName;
    private final EventBusName partitionedEventBusName;

    public PartitionAwareNamingStrategy(EventBusName baseEventBusName, int partitionNumber) {
        Preconditions.checkArgument(partitionNumber > 0, "PartitionAwareNamingStrategy should work only with positive partitions");

        this.baseEventBusName = baseEventBusName;
        this.partitionedEventBusName = suffixedEventBusName(baseEventBusName, partitionNumber);
    }

    @Override
    public RegistrationQueueName queueName(EventBusId eventBusId) {
        return new RegistrationQueueName(partitionedEventBusName.value() + "-eventbus-" + eventBusId.asString());
    }

    @Override
    public QueueSpecification deadLetterQueue() {
        return QueueSpecification.queue(baseEventBusName.value() + "-dead-letter-queue");
    }

    @Override
    public String exchange() {
        return partitionedEventBusName.value() + "-exchange";
    }

    @Override
    public String deadLetterExchange() {
        return baseEventBusName.value() + "-dead-letter-exchange";
    }

    @Override
    public GroupConsumerRetry.RetryExchangeName retryExchange(Group group) {
        return new GroupConsumerRetry.RetryExchangeName(partitionedEventBusName.value(), group);
    }

    @Override
    public GroupRegistration.WorkQueueName workQueue(Group group) {
        return new GroupRegistration.WorkQueueName(partitionedEventBusName.value(), group);
    }

    @Override
    public EventBusName getEventBusName() {
        return partitionedEventBusName;
    }

    private EventBusName suffixedEventBusName(EventBusName baseEventBusName, int partitionNumber) {
        return new EventBusName(baseEventBusName.value() + "-" + partitionNumber);
    }
}
