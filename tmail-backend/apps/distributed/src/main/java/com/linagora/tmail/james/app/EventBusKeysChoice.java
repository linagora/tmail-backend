package com.linagora.tmail.james.app;

import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;

public enum EventBusKeysChoice {
    RABBITMQ,
    REDIS;

    private static EventBusKeysChoice parse(Configuration configuration) {
        return Optional.ofNullable(configuration.getString("event.bus.keys.choice", null))
            .map(value -> EventBusKeysChoice.valueOf(value.toUpperCase(Locale.US)))
            .orElse(EventBusKeysChoice.RABBITMQ);
    }

    public static EventBusKeysChoice parse(PropertiesProvider configuration) {
        try {
            return parse(configuration.getConfiguration("queue"));
        } catch (FileNotFoundException e) {
            return RABBITMQ;
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
