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

import org.apache.commons.lang3.StringUtils;

public record KeyChannelMessage(EventBusId eventBusId, String routingKey, String eventAsJson) {
    public static final String REDIS_CHANNEL_MESSAGE_DELIMITER = "|||";

    static KeyChannelMessage from(EventBusId eventBusId, RoutingKeyConverter.RoutingKey routingKey, String eventAsJson) {
        return new KeyChannelMessage(eventBusId, routingKey.asString(), eventAsJson);
    }

    static KeyChannelMessage parse(String channelMessage) {
        try {
            int maxParts = 3;
            String[] parts = StringUtils.split(channelMessage, REDIS_CHANNEL_MESSAGE_DELIMITER, maxParts);
            EventBusId eventBusId = EventBusId.of(parts[0]);
            String routingKey = parts[1];
            String eventAsJson = parts[2];

            return new KeyChannelMessage(eventBusId, routingKey, eventAsJson);
        } catch (Exception e) {
            throw new RuntimeException("Can not parse the Redis event bus keys channel message", e);
        }
    }

    public String serialize() {
        return eventBusId.asString() + REDIS_CHANNEL_MESSAGE_DELIMITER + routingKey + REDIS_CHANNEL_MESSAGE_DELIMITER + eventAsJson;
    }
}
