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

import java.util.List;
import java.util.stream.IntStream;

public class TmailNamingStrategyFactory {
    private final EventBusName baseEventBusName;
    private final TmailRabbitEventBusConfiguration tmailRabbitEventBusConfiguration;

    public TmailNamingStrategyFactory(EventBusName baseEventBusName, TmailRabbitEventBusConfiguration tmailRabbitEventBusConfiguration) {
        this.baseEventBusName = baseEventBusName;
        this.tmailRabbitEventBusConfiguration = tmailRabbitEventBusConfiguration;
    }

    public List<NamingStrategy> namingStrategies() {
        return IntStream.range(0, tmailRabbitEventBusConfiguration.partitionCount())
            .mapToObj(this::toNamingStrategy)
            .toList();
    }

    public List<String> groupWorkQueueNames(Group group) {
        return namingStrategies().stream()
            .map(namingStrategy -> namingStrategy.workQueue(group).asString())
            .toList();
    }

    private NamingStrategy toNamingStrategy(int partition) {
        if (partition == 0) {
            return new DefaultNamingStrategy(baseEventBusName);
        }

        return new PartitionAwareNamingStrategy(baseEventBusName, partition);
    }
}
