package org.apache.james.events;

import static org.apache.james.events.PostgresEventBusModule.PostgresEventBusNotificationBindingsTable.ROUTING_KEY_INDEX;
import static org.apache.james.events.PostgresEventBusModule.PostgresEventBusNotificationBindingsTable.TABLE;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresEventBusModule {
    interface PostgresEventBusNotificationBindingsTable {
        Table<Record> TABLE_NAME = DSL.table("event_bus_notification_bindings");

        Field<String> ROUTING_KEY = DSL.field("routing_key", SQLDataType.VARCHAR.notNull());
        Field<String> CHANNEL = DSL.field("channel", SQLDataType.VARCHAR.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(ROUTING_KEY)
                .column(CHANNEL)
                .constraint(DSL.primaryKey(ROUTING_KEY, CHANNEL))))
            .disableRowLevelSecurity()
            .build();

        PostgresIndex ROUTING_KEY_INDEX = PostgresIndex.name("eventbus_bindings_routing_key_index")
            .createIndexStep((dsl, indexName) -> dsl.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, ROUTING_KEY));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .addIndex(ROUTING_KEY_INDEX)
        .build();
}
