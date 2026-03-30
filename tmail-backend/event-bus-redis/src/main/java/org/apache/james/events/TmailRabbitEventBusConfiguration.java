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

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Preconditions;

public record TmailRabbitEventBusConfiguration(int partitionCount) {
    public static final String PARTITION_COUNT_PROPERTY = "event.bus.partition.count";
    public static final int DEFAULT_PARTITION_COUNT = 1;
    public static final TmailRabbitEventBusConfiguration DEFAULT = new TmailRabbitEventBusConfiguration(DEFAULT_PARTITION_COUNT);

    public TmailRabbitEventBusConfiguration {
        Preconditions.checkArgument(partitionCount > 0, "event.bus.partition.count must be strictly positive");
    }

    public static TmailRabbitEventBusConfiguration from(Configuration configuration) {
        return new TmailRabbitEventBusConfiguration(configuration.getInt(PARTITION_COUNT_PROPERTY, DEFAULT_PARTITION_COUNT));
    }
}
