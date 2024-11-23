package com.linagora.tmail.james.jmap.settings;

import static com.linagora.tmail.james.jmap.settings.PostgresJmapSettingsModule.PostgresJmapSettingsTable.TABLE;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.impl.SQLDataType;
import org.jooq.postgres.extensions.bindings.HstoreBinding;
import org.jooq.postgres.extensions.types.Hstore;

public interface PostgresJmapSettingsModule {
    interface PostgresJmapSettingsTable {
        Table<Record> TABLE_NAME = DSL.table("jmap_settings");

        Field<String> USER = DSL.field("username", SQLDataType.VARCHAR.notNull());
        Field<UUID> STATE = DSL.field("state", SQLDataType.UUID.notNull());
        Field<Hstore> SETTINGS = DSL.field("settings", DefaultDataType.getDefaultDataType("hstore").asConvertedDataType(new HstoreBinding()).notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USER)
                .column(STATE)
                .column(SETTINGS)
                .primaryKey(USER)))
            .supportsRowLevelSecurity()
            .build();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .build();
}
