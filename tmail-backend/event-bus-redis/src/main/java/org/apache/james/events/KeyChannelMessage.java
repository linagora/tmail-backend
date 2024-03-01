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
