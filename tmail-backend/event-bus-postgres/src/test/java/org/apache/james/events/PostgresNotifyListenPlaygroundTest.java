package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import reactor.core.publisher.Mono;

class PostgresNotifyListenPlaygroundTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresModule.EMPTY_MODULE, PostgresExtension.PoolSize.LARGE);

    @Test
    void shouldReceiveNotification() throws InterruptedException {
        String channel = "test-eventbus-4e4c7b03-98d3-4beb-986c-f87e0636dc30";
        String quotedChannel = "\"" + channel + "\"";
        String payload1 = "2483cc11-479a-4132-b076-561cb9f13833|||TestRegistrationKey:a|||org.apache.james.events.EventBusTestFixture.UnsupportedEvent&5a7a9f3f-5f03-44be-b457-a51e93760645&user";
        String payload2 = "Hello, Postgres Notify/Listen 2nd!";

        // Set up a latch to wait for the notification
        CountDownLatch latch = new CountDownLatch(2);

        postgresExtension.getConnection()
            .flatMapMany(connection -> {
                PostgresqlConnection pgConn = (PostgresqlConnection) connection;
                return pgConn.createStatement("LISTEN " + quotedChannel)
                    .execute()
                    .thenMany(pgConn.getNotifications())
                    .doOnNext(notification -> {
                        System.out.println("Received notification: " + notification.getParameter() + " from " + notification.getName());
                        latch.countDown();
                    })
                    .then();
            })
            .subscribe();

        // Publish a notification
        Mono.from(postgresExtension.getConnectionFactory().create())
            .flatMapMany(connection -> Mono.from(connection.createStatement("NOTIFY " + quotedChannel + ", '" + payload1 + "'")
                    .execute())
                .flatMap(result -> Mono.from(result.getRowsUpdated())))
            .collectList()
            .then()
            .block();

        Mono.from(postgresExtension.getConnectionFactory().create())
            .flatMapMany(connection -> Mono.from(connection.createStatement("NOTIFY " + quotedChannel + ", '" + payload2 + "'")
                    .execute())
                .flatMap(result -> Mono.from(result.getRowsUpdated())))
            .collectList()
            .then()
            .block();

        // Wait for the latch to count down
        latch.await();

        // Assert that the notification was received
        assertThat(latch.getCount()).isZero();
    }

}
