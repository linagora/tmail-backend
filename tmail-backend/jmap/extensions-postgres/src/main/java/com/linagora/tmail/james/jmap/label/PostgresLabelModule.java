package com.linagora.tmail.james.jmap.label;

import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelChangeTable.INDEX;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelChangeTable.TABLE;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresLabelModule {
    interface LabelChangeTable {
        Table<Record> TABLE_NAME = DSL.table("label_change");

        Field<String> ACCOUNT_ID = DSL.field("account_id", SQLDataType.VARCHAR.notNull());
        Field<UUID> STATE = DSL.field("state", SQLDataType.UUID.notNull());
        Field<String[]> CREATED = DSL.field("created", SQLDataType.VARCHAR.getArrayDataType().notNull());
        Field<String[]> UPDATED = DSL.field("updated", SQLDataType.VARCHAR.getArrayDataType().notNull());
        Field<String[]> DESTROYED = DSL.field("destroyed", SQLDataType.VARCHAR.getArrayDataType().notNull());
        Field<OffsetDateTime> CREATED_DATE = DSL.field("created_date", SQLDataType.TIMESTAMPWITHTIMEZONE.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(ACCOUNT_ID)
                .column(STATE)
                .column(CREATED)
                .column(UPDATED)
                .column(DESTROYED)
                .column(CREATED_DATE)
                .constraint(DSL.primaryKey(ACCOUNT_ID, STATE))))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex INDEX = PostgresIndex.name("index_label_change_created_date")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, CREATED_DATE));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .addIndex(INDEX)
        .build();
}
