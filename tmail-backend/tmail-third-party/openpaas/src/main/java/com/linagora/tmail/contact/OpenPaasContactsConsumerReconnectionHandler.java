package com.linagora.tmail.contact;

import jakarta.inject.Inject;

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.reactivestreams.Publisher;

import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;

public class OpenPaasContactsConsumerReconnectionHandler implements SimpleConnectionPool.ReconnectionHandler {
    private final OpenPaasContactsConsumer openPaasContactsConsumer;

    @Inject
    public OpenPaasContactsConsumerReconnectionHandler(OpenPaasContactsConsumer openPaasContactsConsumer) {
        this.openPaasContactsConsumer = openPaasContactsConsumer;
    }

    @Override
    public Publisher<Void> handleReconnection(Connection connection) {
        return Mono.fromRunnable(openPaasContactsConsumer::restart);
    }
}