package com.linagora.tmail.james.jmap.label;

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
                .constraint(DSL.primaryKey(ACCOUNT_ID, STATE))
                .comment("Hold JMAP label changes")))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex INDEX = PostgresIndex.name("index_label_change_created_date")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, CREATED_DATE));
    }

    interface LabelTable {
        Table<Record> TABLE_NAME = DSL.table("labels");

        Field<String> USERNAME = DSL.field("username", SQLDataType.VARCHAR.notNull());
        Field<String> KEYWORD = DSL.field("keyword", SQLDataType.VARCHAR.notNull());
        Field<String> DISPLAY_NAME = DSL.field("display_name", SQLDataType.VARCHAR.notNull());
        Field<String> COLOR = DSL.field("color", SQLDataType.VARCHAR);

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USERNAME)
                .column(KEYWORD)
                .column(DISPLAY_NAME)
                .column(COLOR)
                .constraint(DSL.primaryKey(USERNAME, KEYWORD))
                .comment("Hold user JMAP labels")))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex USERNAME_INDEX = PostgresIndex.name("index_labels_username")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, USERNAME));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(LabelChangeTable.TABLE, LabelTable.TABLE)
        .addIndex(LabelChangeTable.INDEX, LabelTable.USERNAME_INDEX)
        .build();
}
